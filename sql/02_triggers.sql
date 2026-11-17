SET NAMES utf8mb4;
USE quindiostay;

-- Limpieza para poder repetir el script
DROP TRIGGER IF EXISTS trg_cliente_bi;
DROP TRIGGER IF EXISTS trg_cliente_bu;
DROP TRIGGER IF EXISTS trg_reserva_bi;
DROP TRIGGER IF EXISTS trg_reserva_bu;
DROP TRIGGER IF EXISTS trg_reserva_au;
DROP TRIGGER IF EXISTS trg_notificacion_bi;
DROP TRIGGER IF EXISTS trg_notificacion_bu;
DROP TRIGGER IF EXISTS trg_usuario_bi;
DROP TRIGGER IF EXISTS trg_usuario_bu;
DROP TRIGGER IF EXISTS trg_movimiento_bi;
DROP TRIGGER IF EXISTS trg_movimiento_bu;
DROP TRIGGER IF EXISTS trg_movimiento_bd;
DROP TRIGGER IF EXISTS trg_redencion_bi;
DROP PROCEDURE IF EXISTS chk_cliente;
DROP PROCEDURE IF EXISTS chk_reserva_plan;
DROP PROCEDURE IF EXISTS chk_notificacion;
DROP PROCEDURE IF EXISTS chk_usuario;
DROP PROCEDURE IF EXISTS sp_redimir;
DROP FUNCTION  IF EXISTS fn_acumulado_12m;
DROP VIEW      IF EXISTS v_nivel_membresia;

DELIMITER $$


-- A. FUNCIONES DE APOYO


-- Suma de las acumulaciones de una membresía en los 12 meses anteriores a una fecha. Es la base del nivel (regla 8): las redenciones y los
-- vencimientos NO la reducen
CREATE FUNCTION fn_acumulado_12m(p_id_membresia INT, p_fecha DATETIME)
RETURNS BIGINT
READS SQL DATA
BEGIN
    DECLARE v_total BIGINT;
    SELECT COALESCE(SUM(cantidad), 0) INTO v_total
    FROM movimiento_puntos
    WHERE id_membresia = p_id_membresia
      AND tipo = 'acumulación'
      AND fecha >  DATE_SUB(p_fecha, INTERVAL 12 MONTH)
      AND fecha <= p_fecha;
    RETURN v_total;
END$$


-- B. VALIDACIONES DEL NÚCLEO


-- ---- cliente: la fecha de nacimiento no puede ser futura -------------
-- (un CHECK no admite CURDATE() en MySQL 8)
CREATE PROCEDURE chk_cliente(IN p_fecha_nacimiento DATE)
BEGIN
    IF p_fecha_nacimiento > CURDATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La fecha de nacimiento no puede ser futura.';
    END IF;
END$$

CREATE TRIGGER trg_cliente_bi BEFORE INSERT ON cliente
FOR EACH ROW
BEGIN
    CALL chk_cliente(NEW.fecha_nacimiento);
END$$

CREATE TRIGGER trg_cliente_bu BEFORE UPDATE ON cliente
FOR EACH ROW
BEGIN
    CALL chk_cliente(NEW.fecha_nacimiento);
END$$

-- ---- reserva: el plan debe estar activo y vigente en la fecha del servicio
CREATE PROCEDURE chk_reserva_plan(IN p_id_plan INT, IN p_fecha_servicio DATE)
BEGIN
    DECLARE v_estado VARCHAR(10);
    DECLARE v_inicio DATE;
    DECLARE v_fin    DATE;

    SELECT estado, fecha_inicio, fecha_fin INTO v_estado, v_inicio, v_fin
    FROM plan WHERE id_plan = p_id_plan;

    IF v_estado IS NOT NULL THEN
        IF v_estado <> 'activo' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'El plan no está activo.';
        END IF;
        IF p_fecha_servicio < v_inicio OR p_fecha_servicio > v_fin THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'El plan no está vigente en la fecha del servicio.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_reserva_bi BEFORE INSERT ON reserva
FOR EACH ROW
BEGIN
    CALL chk_reserva_plan(NEW.id_plan, NEW.fecha_servicio);
END$$

-- ---- reserva: transiciones de estado permitidas 
--   pendiente  -> confirmada | cancelada
--   confirmada -> completada | cancelada
--   cancelada y completada son finales.
-- Para completar hace falta el pago total (suma de pagos exitosos >= valor_total), como dice RF-04
CREATE TRIGGER trg_reserva_bu BEFORE UPDATE ON reserva
FOR EACH ROW
BEGIN
    DECLARE v_pagado DECIMAL(14,2);

    IF NEW.id_plan <> OLD.id_plan OR NEW.fecha_servicio <> OLD.fecha_servicio THEN
        CALL chk_reserva_plan(NEW.id_plan, NEW.fecha_servicio);
    END IF;

    IF NEW.estado <> OLD.estado THEN
        IF OLD.estado IN ('cancelada', 'completada') THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Una reserva cancelada o completada ya no puede cambiar de estado.';
        END IF;
        IF NEW.estado = 'completada' AND OLD.estado <> 'confirmada' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Una reserva solo puede pasar a completada si antes estuvo confirmada.';
        END IF;
        IF NEW.estado = 'confirmada' AND OLD.estado <> 'pendiente' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Solo una reserva pendiente puede pasar a confirmada.';
        END IF;
        IF NEW.estado = 'pendiente' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Una reserva no puede volver a pendiente.';
        END IF;
        IF NEW.estado = 'completada' THEN
            SELECT COALESCE(SUM(monto), 0) INTO v_pagado
            FROM pago
            WHERE id_reserva = OLD.id_reserva AND estado = 'exitoso';
            IF v_pagado < NEW.valor_total THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'No se puede completar: el pago total aún no está confirmado.';
            END IF;
        END IF;
    END IF;
END$$

-- ---- notificacion: coherencia con la reserva y con las promociones 
--   * Si trae reserva, el cliente debe ser el dueño de esa reserva
--   * Las promociones solo se envían a clientes que las aceptaron
CREATE PROCEDURE chk_notificacion(IN p_tipo VARCHAR(30), IN p_id_cliente INT,
                                  IN p_id_reserva INT)
BEGIN
    DECLARE v_cliente_reserva INT;
    DECLARE v_acepta TINYINT(1);

    IF p_id_reserva IS NOT NULL THEN
        SELECT id_cliente INTO v_cliente_reserva
        FROM reserva WHERE id_reserva = p_id_reserva;
        IF v_cliente_reserva IS NOT NULL AND v_cliente_reserva <> p_id_cliente THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'La reserva de la notificación pertenece a otro cliente.';
        END IF;
    END IF;

    IF p_tipo = 'promoción' THEN
        SELECT acepta_promociones INTO v_acepta
        FROM perfil_cliente WHERE id_cliente = p_id_cliente;
        IF v_acepta IS NULL OR v_acepta <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'El cliente no aceptó recibir promociones.';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_notificacion_bi BEFORE INSERT ON notificacion
FOR EACH ROW
BEGIN
    CALL chk_notificacion(NEW.tipo, NEW.id_cliente, NEW.id_reserva);
END$$

CREATE TRIGGER trg_notificacion_bu BEFORE UPDATE ON notificacion
FOR EACH ROW
BEGIN
    CALL chk_notificacion(NEW.tipo, NEW.id_cliente, NEW.id_reserva);
END$$

-- ---- usuario_sistema: solo el encargado de prestador tiene prestador ---
CREATE PROCEDURE chk_usuario(IN p_id_rol INT, IN p_id_prestador INT)
BEGIN
    DECLARE v_rol VARCHAR(30);
    SELECT nombre INTO v_rol FROM rol WHERE id_rol = p_id_rol;

    IF v_rol = 'encargado de prestador' AND p_id_prestador IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El encargado de prestador debe tener un prestador asignado.';
    END IF;
    IF v_rol IS NOT NULL AND v_rol <> 'encargado de prestador'
       AND p_id_prestador IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo el encargado de prestador puede tener un prestador asignado.';
    END IF;
END$$

CREATE TRIGGER trg_usuario_bi BEFORE INSERT ON usuario_sistema
FOR EACH ROW
BEGIN
    CALL chk_usuario(NEW.id_rol, NEW.id_prestador);
END$$

CREATE TRIGGER trg_usuario_bu BEFORE UPDATE ON usuario_sistema
FOR EACH ROW
BEGIN
    CALL chk_usuario(NEW.id_rol, NEW.id_prestador);
END$$




-- C. AUTOMATISMOS AL CAMBIAR EL ESTADO DE UNA RESERVA
-- =====================================================================

-- Al pasar a completada se genera la acumulación de puntos (RF-04) si:
--   * el cliente tiene membresía,
--   * el prestador del plan es aliado,
--   * la fecha del servicio es igual o posterior a la inscripción (los puntos no son retroactivos, regla 1),
--   * los puntos resultantes son mayores que cero.
-- Puntos = parte entera de (valor_total / 1000) * puntos_por_1000 (regla 4).
--
-- Al pasar a cancelada con más de 5 días de anticipación se registra el reembolso del 80 % de lo pagado con éxito
CREATE TRIGGER trg_reserva_au AFTER UPDATE ON reserva
FOR EACH ROW
BEGIN
    DECLARE v_pagado DECIMAL(14,2);
    DECLARE v_metodo VARCHAR(20);

    IF NEW.estado = 'completada' AND OLD.estado <> 'completada' THEN
        INSERT INTO movimiento_puntos (fecha, cantidad, tipo, id_membresia, id_reserva)
        SELECT NOW(),
               FLOOR(NEW.valor_total / 1000) * pa.puntos_por_1000,
               'acumulación',
               mb.id_membresia,
               NEW.id_reserva
        FROM membresia mb
        JOIN plan pl             ON pl.id_plan = NEW.id_plan
        JOIN prestador_aliado pa ON pa.id_prestador = pl.id_prestador
        WHERE mb.id_cliente = NEW.id_cliente
          AND NEW.fecha_servicio >= mb.fecha_inscripcion
          AND FLOOR(NEW.valor_total / 1000) * pa.puntos_por_1000 > 0;
    END IF;

    IF NEW.estado = 'cancelada' AND OLD.estado <> 'cancelada'
       AND DATEDIFF(NEW.fecha_servicio, CURDATE()) > 5 THEN
        SELECT COALESCE(SUM(monto), 0) INTO v_pagado
        FROM pago WHERE id_reserva = NEW.id_reserva AND estado = 'exitoso';

        IF v_pagado > 0 THEN
            SELECT metodo INTO v_metodo
            FROM pago
            WHERE id_reserva = NEW.id_reserva AND estado = 'exitoso'
            ORDER BY fecha DESC, id_pago DESC
            LIMIT 1;

            INSERT INTO pago (fecha, monto, metodo, estado, referencia_transaccion, id_reserva)
            VALUES (NOW(), ROUND(v_pagado * 0.80, 2), v_metodo, 'reembolsado',
                    CONCAT('REEMB-', NEW.id_reserva), NEW.id_reserva);
        END IF;
    END IF;
END$$




-- D. REGLAS DE PUNTOS


-- Antes de insertar un movimiento se validan:
--  Regla 1: la acumulación viene de una reserva del mismo cliente dueño
--           de la membresía y con fecha de servicio >= inscripción.
--  Regla 2: solo genera puntos un prestador aliado (la reserva debe ser
--           de un plan de un prestador que esté en prestador_aliado).
--  Regla 3: una reserva acumula una sola vez (UNIQUE en el DDL).
--  Regla 4: puntos = FLOOR(valor_total / 1000) * puntos_por_1000.
--  Regla 6: un vencimiento solo cubre puntos ganados hace 12 meses o
--           más que no hayan sido consumidos (las redenciones consumen
--           primero los más antiguos).
--  Saldo:   ningún movimiento deja el saldo de la membresía negativo.
-- Solo cuentan reservas completadas.
CREATE TRIGGER trg_movimiento_bi BEFORE INSERT ON movimiento_puntos
FOR EACH ROW
BEGIN
    DECLARE v_fecha        DATETIME;
    DECLARE v_cli_mb       INT;
    DECLARE v_inscripcion  DATE;
    DECLARE v_cli_res      INT;
    DECLARE v_estado_res   VARCHAR(12);
    DECLARE v_valor        DECIMAL(12,2);
    DECLARE v_fserv        DATE;
    DECLARE v_prestador    INT;
    DECLARE v_p1000        INT;
    DECLARE v_saldo        BIGINT;
    DECLARE v_ganado_viejo BIGINT;
    DECLARE v_consumido    BIGINT;
    DECLARE v_vencible     BIGINT;

    SET v_fecha = COALESCE(NEW.fecha, NOW());

    SELECT id_cliente, fecha_inscripcion INTO v_cli_mb, v_inscripcion
    FROM membresia WHERE id_membresia = NEW.id_membresia;

    IF NEW.tipo = 'acumulación' THEN
        SELECT r.id_cliente, r.estado, r.valor_total, r.fecha_servicio, pl.id_prestador
          INTO v_cli_res, v_estado_res, v_valor, v_fserv, v_prestador
        FROM reserva r
        JOIN plan pl ON pl.id_plan = r.id_plan
        WHERE r.id_reserva = NEW.id_reserva;

        IF v_cli_res <> v_cli_mb THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Regla 1: la reserva no pertenece al cliente dueño de la membresía.';
        END IF;
        IF v_fserv < v_inscripcion THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Regla 1: los puntos no son retroactivos (servicio anterior a la inscripción).';
        END IF;
        IF v_estado_res <> 'completada' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Solo las reservas completadas generan puntos.';
        END IF;

        SELECT puntos_por_1000 INTO v_p1000
        FROM prestador_aliado WHERE id_prestador = v_prestador;
        IF v_p1000 IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Regla 2: el prestador de la reserva no es aliado.';
        END IF;
        IF NEW.cantidad <> FLOOR(v_valor / 1000) * v_p1000 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Regla 4: los puntos no corresponden al valor de la reserva.';
        END IF;
    END IF;

    IF NEW.cantidad < 0 THEN
        SELECT COALESCE(SUM(cantidad), 0) INTO v_saldo
        FROM movimiento_puntos WHERE id_membresia = NEW.id_membresia;
        IF v_saldo + NEW.cantidad < 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Saldo insuficiente: el saldo no puede quedar negativo.';
        END IF;
    END IF;

    IF NEW.tipo = 'vencimiento' THEN
        SELECT COALESCE(SUM(cantidad), 0) INTO v_ganado_viejo
        FROM movimiento_puntos
        WHERE id_membresia = NEW.id_membresia
          AND tipo = 'acumulación'
          AND fecha <= DATE_SUB(v_fecha, INTERVAL 12 MONTH);

        SELECT COALESCE(-SUM(cantidad), 0) INTO v_consumido
        FROM movimiento_puntos
        WHERE id_membresia = NEW.id_membresia AND cantidad < 0;

        SET v_vencible = GREATEST(0, v_ganado_viejo - v_consumido);
        IF -NEW.cantidad > v_vencible THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Regla 6: solo vencen puntos ganados hace 12 meses o más y no consumidos.';
        END IF;
    END IF;
END$$

-- El historial no se modifica (RF-05). Una corrección se hace con un movimiento de ajuste manual. Solo se permite borrar ajustes manuales
CREATE TRIGGER trg_movimiento_bu BEFORE UPDATE ON movimiento_puntos
FOR EACH ROW
BEGIN
    IF NEW.fecha        <> OLD.fecha
       OR NEW.cantidad  <> OLD.cantidad
       OR NEW.tipo      <> OLD.tipo
       OR NEW.id_membresia <> OLD.id_membresia
       OR NOT (NEW.id_reserva <=> OLD.id_reserva) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Los movimientos son historial y no se modifican; use un ajuste manual.';
    END IF;
END$$

CREATE TRIGGER trg_movimiento_bd BEFORE DELETE ON movimiento_puntos
FOR EACH ROW
BEGIN
    IF OLD.tipo <> 'ajuste manual' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Solo se pueden eliminar movimientos de ajuste manual.';
    END IF;
END$$

-- Regla 7 y nivel mínimo en la redención:
--  * el movimiento debe ser de tipo redención,
--  * descuenta exactamente el costo_puntos vigente del beneficio,
--  * el cliente debe tener el nivel del beneficio o uno superior (nivel calculado con las acumulaciones de los últimos 12 meses, regla 8).
CREATE TRIGGER trg_redencion_bi BEFORE INSERT ON redencion
FOR EACH ROW
BEGIN
    DECLARE v_tipo      VARCHAR(15);
    DECLARE v_cantidad  INT;
    DECLARE v_membresia INT;
    DECLARE v_fecha     DATETIME;
    DECLARE v_costo     INT;
    DECLARE v_umbral    INT;

    SELECT tipo, cantidad, id_membresia, fecha
      INTO v_tipo, v_cantidad, v_membresia, v_fecha
    FROM movimiento_puntos WHERE id_movimiento = NEW.id_movimiento;

    SELECT b.costo_puntos, n.umbral_minimo INTO v_costo, v_umbral
    FROM beneficio b JOIN nivel n ON n.id_nivel = b.id_nivel
    WHERE b.id_beneficio = NEW.id_beneficio;

    IF v_tipo <> 'redención' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El movimiento asociado a una redención debe ser de tipo redención.';
    END IF;
    IF v_cantidad <> -v_costo THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Regla 7: la redención debe descontar exactamente el costo vigente del beneficio.';
    END IF;
    IF fn_acumulado_12m(v_membresia, v_fecha) < v_umbral THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El beneficio es de un nivel superior al del cliente.';
    END IF;
END$$




-- E. PROCEDIMIENTO DE REDENCIÓN (RF-07) Y VISTA DEL NIVEL
-- Canjea un beneficio: crea el movimiento y la redención en UNA sola transacción. Si cualquier regla falla, no queda nada a medias
CREATE PROCEDURE sp_redimir(IN p_id_membresia INT, IN p_id_beneficio INT)
BEGIN
    DECLARE v_costo INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT costo_puntos INTO v_costo FROM beneficio WHERE id_beneficio = p_id_beneficio;
    IF v_costo IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El beneficio no existe.';
    END IF;

    START TRANSACTION;
    INSERT INTO movimiento_puntos (fecha, cantidad, tipo, id_membresia, id_reserva)
    VALUES (NOW(), -v_costo, 'redención', p_id_membresia, NULL);
    INSERT INTO redencion (id_movimiento, id_beneficio)
    VALUES (LAST_INSERT_ID(), p_id_beneficio);
    COMMIT;
END$$

DELIMITER ;

-- Nivel de cada membresía (regla 8): el más alto cuyo umbral_minimo no supera la suma de sus acumulaciones de los últimos 12 meses. Como el
-- umbral más bajo es 0, todos los miembros tienen nivel
CREATE VIEW v_nivel_membresia AS
SELECT t.id_membresia,
       t.id_cliente,
       t.acumulado_12m,
       (SELECT n.nombre
          FROM nivel n
         WHERE n.umbral_minimo <= t.acumulado_12m
         ORDER BY n.umbral_minimo DESC
         LIMIT 1) AS nivel
FROM (SELECT mb.id_membresia,
             mb.id_cliente,
             fn_acumulado_12m(mb.id_membresia, NOW()) AS acumulado_12m
      FROM membresia mb) t;


