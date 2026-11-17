SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS quindiostay
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE quindiostay;


-- 0. Limpieza: borra todo en orden inverso (hijas primero) para poder
--    repetir el script todas las veces que se quiera
SET FOREIGN_KEY_CHECKS = 0;
DROP VIEW  IF EXISTS v_movimiento_aliado;
DROP VIEW  IF EXISTS v_saldo_membresia;
DROP TABLE IF EXISTS redencion;
DROP TABLE IF EXISTS movimiento_puntos;
DROP TABLE IF EXISTS beneficio;
DROP TABLE IF EXISTS prestador_aliado;
DROP TABLE IF EXISTS membresia;
DROP TABLE IF EXISTS nivel;
DROP TABLE IF EXISTS usuario_sistema;
DROP TABLE IF EXISTS rol;
DROP TABLE IF EXISTS notificacion;
DROP TABLE IF EXISTS pago;
DROP TABLE IF EXISTS reserva;
DROP TABLE IF EXISTS plan;
DROP TABLE IF EXISTS perfil_cliente;
DROP TABLE IF EXISTS cliente;
DROP TABLE IF EXISTS prestador;
DROP TABLE IF EXISTS tipo_prestador;
DROP TABLE IF EXISTS municipio;
SET FOREIGN_KEY_CHECKS = 1;


-- 1. NÚCLEO COMÚN

-- los 12 municipios del Quindío Ni el nombre ni el código DANE se repiten
CREATE TABLE municipio (
    id_municipio  INT AUTO_INCREMENT,
    nombre        VARCHAR(80) NOT NULL,
    codigo_dane   CHAR(5)     NOT NULL,
    CONSTRAINT pk_municipio        PRIMARY KEY (id_municipio),
    CONSTRAINT uq_municipio_nombre UNIQUE (nombre),
    CONSTRAINT uq_municipio_dane   UNIQUE (codigo_dane),
    CONSTRAINT ck_municipio_dane   CHECK (codigo_dane REGEXP '^[0-9]{5}$')
) ENGINE=InnoDB COMMENT='Municipios del Quindío con su código DANE';



-- tipo_prestador: catálogo con alojamiento, transporte, gastronomía...
CREATE TABLE tipo_prestador (
    id_tipo_prestador  INT AUTO_INCREMENT,
    nombre             VARCHAR(50) NOT NULL,
    CONSTRAINT pk_tipo_prestador        PRIMARY KEY (id_tipo_prestador),
    CONSTRAINT uq_tipo_prestador_nombre UNIQUE (nombre)
) ENGINE=InnoDB COMMENT='Catálogo de tipos de prestador';

-- prestador: empresa o persona que ofrece servicios turísticos
-- Un prestador retirado NO se borra: se cambia su estado. Por eso las FK que lo apuntan son RESTRICT
CREATE TABLE prestador (
    id_prestador       INT AUTO_INCREMENT,
    nombre_comercial   VARCHAR(120) NOT NULL,
    nit_documento      VARCHAR(20)  NOT NULL,
    rnt                VARCHAR(20)  NULL COMMENT 'Registro Nacional de Turismo',
    direccion          VARCHAR(150) NOT NULL,
    telefono           VARCHAR(20)  NOT NULL,
    correo             VARCHAR(100) NOT NULL,
    estado             VARCHAR(12)  NOT NULL DEFAULT 'activo',
    id_municipio       INT NOT NULL,
    id_tipo_prestador  INT NOT NULL,
    CONSTRAINT pk_prestador PRIMARY KEY (id_prestador),
    CONSTRAINT uq_prestador_nit UNIQUE (nit_documento),
    CONSTRAINT ck_prestador_estado
        CHECK (estado IN ('activo', 'suspendido', 'retirado')),
    CONSTRAINT ck_prestador_correo CHECK (correo LIKE '%_@_%._%'),
    CONSTRAINT fk_prestador_municipio
        FOREIGN KEY (id_municipio) REFERENCES municipio (id_municipio)
        ON DELETE RESTRICT,
    CONSTRAINT fk_prestador_tipo
        FOREIGN KEY (id_tipo_prestador) REFERENCES tipo_prestador (id_tipo_prestador)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Prestadores de servicios turísticos';


-- cliente: persona identificada con tipo_documento, numero_documento
-- contrasena_cifrada es NULL cuando el cliente se registró en el  mostrador y todavía no tiene cuenta. El login usa el correo
CREATE TABLE cliente (
    id_cliente          INT AUTO_INCREMENT,
    tipo_documento      VARCHAR(25)  NOT NULL,
    numero_documento    VARCHAR(20)  NOT NULL,
    nombres             VARCHAR(80)  NOT NULL,
    apellidos           VARCHAR(80)  NOT NULL,
    correo              VARCHAR(100) NOT NULL,
    telefono            VARCHAR(20)  NULL,
    fecha_nacimiento    DATE         NOT NULL,
    ciudad_origen       VARCHAR(80)  NULL,
    contrasena_cifrada  VARCHAR(255) NULL COMMENT 'Hash; NULL si no tiene cuenta',
    CONSTRAINT pk_cliente PRIMARY KEY (id_cliente),
    CONSTRAINT uq_cliente_documento UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT uq_cliente_correo    UNIQUE (correo),
    CONSTRAINT ck_cliente_tipo_documento
        CHECK (tipo_documento IN ('cédula de ciudadanía', 'cédula de extranjería',
                                  'pasaporte', 'tarjeta de identidad')),
    CONSTRAINT ck_cliente_correo CHECK (correo LIKE '%_@_%._%')
) ENGINE=InnoDB COMMENT='Clientes; la contraseña es NULL si no tienen cuenta';


-- perfil_cliente: preferencias del cliente (relación 1:1, entidad débil). Sin cliente no tiene sentido: ON DELETE CASCADE
CREATE TABLE perfil_cliente (
    id_cliente                 INT NOT NULL,
    idioma                     VARCHAR(10) NOT NULL DEFAULT 'español',
    canal_preferido            VARCHAR(10) NOT NULL DEFAULT 'correo',
    anticipacion_recordatorio  SMALLINT    NOT NULL DEFAULT 3,
    acepta_promociones         TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '1 = sí, 0 = no',
    CONSTRAINT pk_perfil_cliente PRIMARY KEY (id_cliente),
    CONSTRAINT ck_perfil_idioma CHECK (idioma IN ('español', 'inglés')),
    CONSTRAINT ck_perfil_canal  CHECK (canal_preferido IN ('correo', 'SMS', 'Whatsapp')),
    CONSTRAINT ck_perfil_anticipacion CHECK (anticipacion_recordatorio IN (1, 3, 7)),
    CONSTRAINT ck_perfil_promociones  CHECK (acepta_promociones IN (0, 1)),
    CONSTRAINT fk_perfil_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
        ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Preferencias del cliente (idioma, canal, recordatorios)';



-- plan: opciones que ofrece un prestador. Un plan vendido no se borra, se inactiva por eso la FK de reserva es RESTRICT
CREATE TABLE plan (
    id_plan       INT AUTO_INCREMENT,
    nombre        VARCHAR(100)  NOT NULL,
    descripcion   TEXT          NULL,
    tipo          VARCHAR(15)   NOT NULL,
    precio        DECIMAL(12,2) NOT NULL,
    fecha_inicio  DATE          NOT NULL,
    fecha_fin     DATE          NOT NULL,
    estado        VARCHAR(10)   NOT NULL DEFAULT 'activo',
    id_prestador  INT NOT NULL,
    CONSTRAINT pk_plan PRIMARY KEY (id_plan),
    CONSTRAINT uq_plan_prestador_nombre UNIQUE (id_prestador, nombre),
    CONSTRAINT ck_plan_tipo
        CHECK (tipo IN ('básico', 'estándar', 'premium', 'todo incluido')),
    CONSTRAINT ck_plan_precio CHECK (precio > 0),
    CONSTRAINT ck_plan_estado CHECK (estado IN ('activo', 'inactivo')),
    CONSTRAINT ck_plan_fechas CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT fk_plan_prestador
        FOREIGN KEY (id_prestador) REFERENCES prestador (id_prestador)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Planes que ofrece cada prestador';



-- reserva: compromiso de un cliente con un plan para una fecha. Una reserva cancelada no se borra (historial). valor_total es el valor pactado: no se deriva del precio del plan
CREATE TABLE reserva (
    id_reserva      INT AUTO_INCREMENT,
    fecha_creacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_servicio  DATE          NOT NULL,
    estado          VARCHAR(12)   NOT NULL DEFAULT 'pendiente',
    valor_total     DECIMAL(12,2) NOT NULL,
    canal           VARCHAR(25)   NOT NULL,
    id_cliente      INT NOT NULL,
    id_plan         INT NOT NULL,
    CONSTRAINT pk_reserva PRIMARY KEY (id_reserva),
    CONSTRAINT ck_reserva_estado
        CHECK (estado IN ('pendiente', 'confirmada', 'cancelada', 'completada')),
    CONSTRAINT ck_reserva_canal
        CHECK (canal IN ('web', 'aplicación móvil', 'teléfono', 'mostrador',
                         'agencia intermediaria')),
    CONSTRAINT ck_reserva_valor CHECK (valor_total > 0),
    CONSTRAINT ck_reserva_fechas CHECK (fecha_servicio >= DATE(fecha_creacion)),
    CONSTRAINT fk_reserva_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_plan
        FOREIGN KEY (id_plan) REFERENCES plan (id_plan)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Reservas de los clientes sobre un plan';



-- pago: abonos de una reserva. Es un registro contable: no se borra con la reserva (RESTRICT). Los pagos con tarjeta o PSE exigen la
-- referencia de la pasarela; la referencia no se repite (UNIQUE admite varios NULL en MySQL)
CREATE TABLE pago (
    id_pago                 INT AUTO_INCREMENT,
    fecha                   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monto                   DECIMAL(12,2) NOT NULL,
    metodo                  VARCHAR(20)   NOT NULL,
    estado                  VARCHAR(12)   NOT NULL DEFAULT 'pendiente',
    referencia_transaccion  VARCHAR(60)   NULL,
    id_reserva              INT NOT NULL,
    CONSTRAINT pk_pago PRIMARY KEY (id_pago),
    CONSTRAINT uq_pago_referencia UNIQUE (referencia_transaccion),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_metodo
        CHECK (metodo IN ('tarjeta de crédito', 'tarjeta débito', 'PSE',
                          'transferencia', 'efectivo')),
    CONSTRAINT ck_pago_estado
        CHECK (estado IN ('exitoso', 'fallido', 'pendiente', 'reembolsado')),
    CONSTRAINT ck_pago_referencia
        CHECK (metodo IN ('transferencia', 'efectivo')
               OR referencia_transaccion IS NOT NULL),
    CONSTRAINT fk_pago_reserva
        FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Pagos y abonos de las reservas';



-- notificacion: mensajes al cliente. id_reserva es opcional (las promociones no tienen reserva) si la reserva se borra, la
-- notificación queda como historial del cliente (SET NULL)
CREATE TABLE notificacion (
    id_notificacion   BIGINT AUTO_INCREMENT,
    tipo              VARCHAR(30)  NOT NULL,
    canal             VARCHAR(10)  NOT NULL,
    mensaje           TEXT         NOT NULL,
    fecha_programada  DATETIME     NOT NULL,
    fecha_envio       DATETIME     NULL,
    estado            VARCHAR(10)  NOT NULL DEFAULT 'pendiente',
    id_cliente        INT NOT NULL,
    id_reserva        INT NULL,
    CONSTRAINT pk_notificacion PRIMARY KEY (id_notificacion),
    CONSTRAINT ck_notificacion_tipo
        CHECK (tipo IN ('recordatorio de reserva', 'confirmación de reserva',
                        'confirmación de pago', 'cambio de estado', 'promoción',
                        'subida de nivel', 'puntos próximos a vencer')),
    CONSTRAINT ck_notificacion_canal CHECK (canal IN ('correo', 'SMS', 'Whatsapp')),
    CONSTRAINT ck_notificacion_estado
        CHECK (estado IN ('pendiente', 'enviada', 'fallida', 'leída')),
    CONSTRAINT ck_notificacion_envio
        CHECK (fecha_envio IS NULL OR fecha_envio >= fecha_programada),
    CONSTRAINT fk_notificacion_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
        ON DELETE RESTRICT,
    CONSTRAINT fk_notificacion_reserva
        FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva)
        ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='Notificaciones y recordatorios a los clientes';



-- rol y usuario_sistema: usuarios internos que inician sesión con usuario y contraseña. Un rol en uso no se borra si desaparece el
-- prestador, el usuario no se borra (la FK es RESTRICT, el prestador tampoco se borra nunca)
CREATE TABLE rol (
    id_rol  INT AUTO_INCREMENT,
    nombre  VARCHAR(30) NOT NULL,
    CONSTRAINT pk_rol PRIMARY KEY (id_rol),
    CONSTRAINT uq_rol_nombre UNIQUE (nombre),
    CONSTRAINT ck_rol_nombre
        CHECK (nombre IN ('administrador', 'encargado de prestador', 'operador'))
) ENGINE=InnoDB COMMENT='Roles de los usuarios internos';

CREATE TABLE usuario_sistema (
    id_usuario          INT AUTO_INCREMENT,
    nombre_usuario      VARCHAR(50)  NOT NULL,
    contrasena_cifrada  VARCHAR(255) NOT NULL COMMENT 'Hash, nunca texto plano',
    estado              VARCHAR(10)  NOT NULL DEFAULT 'activo',
    id_rol              INT NOT NULL,
    id_prestador        INT NULL COMMENT 'Solo para el encargado de prestador',
    CONSTRAINT pk_usuario_sistema PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuario_nombre UNIQUE (nombre_usuario),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('activo', 'bloqueado')),
    CONSTRAINT fk_usuario_rol
        FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
        ON DELETE RESTRICT,
    CONSTRAINT fk_usuario_prestador
        FOREIGN KEY (id_prestador) REFERENCES prestador (id_prestador)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Usuarios internos del sistema';


-- 2. MÓDULO FIDELIZACIÓN  El saldo y el nivel NO se guardan: se calculan con los movimientos



-- nivel: bronce, plata, oro, platino, cada uno con su umbral de puntos acumulados en el último año. El umbral mínimo es 0 para que todos los
-- miembros tengan nivel
CREATE TABLE nivel (
    id_nivel       INT AUTO_INCREMENT,
    nombre         VARCHAR(10) NOT NULL,
    umbral_minimo  INT         NOT NULL,
    CONSTRAINT pk_nivel PRIMARY KEY (id_nivel),
    CONSTRAINT uq_nivel_nombre UNIQUE (nombre),
    CONSTRAINT ck_nivel_nombre CHECK (nombre IN ('bronce', 'plata', 'oro', 'platino')),
    CONSTRAINT ck_nivel_umbral CHECK (umbral_minimo >= 0)
) ENGINE=InnoDB COMMENT='Niveles del programa de fidelización';


-- membresia: inscripción del cliente al programa. Máximo una por cliente (UNIQUE). El historial de puntos no debe perderse: RESTRICT
CREATE TABLE membresia (
    id_membresia       INT AUTO_INCREMENT,
    fecha_inscripcion  DATE NOT NULL DEFAULT (CURRENT_DATE),
    id_cliente         INT  NOT NULL,
    CONSTRAINT pk_membresia PRIMARY KEY (id_membresia),
    CONSTRAINT uq_membresia_cliente UNIQUE (id_cliente),
    CONSTRAINT fk_membresia_cliente
        FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Membresía del programa de puntos (una por cliente)';



-- prestador_aliado: especialización de prestador. Define cuántos puntos da por cada $1.000 gastados.
CREATE TABLE prestador_aliado (
    id_prestador     INT NOT NULL,
    puntos_por_1000  INT NOT NULL,
    CONSTRAINT pk_prestador_aliado PRIMARY KEY (id_prestador),
    CONSTRAINT ck_aliado_puntos CHECK (puntos_por_1000 > 0),
    CONSTRAINT fk_aliado_prestador
        FOREIGN KEY (id_prestador) REFERENCES prestador (id_prestador)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Prestadores aliados que generan puntos';




-- beneficio: lo que se canjea con puntos. Cada beneficio lo entrega un aliado y exige un nivel mínimo
CREATE TABLE beneficio (
    id_beneficio         INT AUTO_INCREMENT,
    nombre               VARCHAR(100) NOT NULL,
    costo_puntos         INT NOT NULL,
    id_prestador_aliado  INT NOT NULL,
    id_nivel             INT NOT NULL COMMENT 'Nivel mínimo para canjearlo',
    CONSTRAINT pk_beneficio PRIMARY KEY (id_beneficio),
    CONSTRAINT ck_beneficio_costo CHECK (costo_puntos > 0),
    CONSTRAINT fk_beneficio_aliado
        FOREIGN KEY (id_prestador_aliado) REFERENCES prestador_aliado (id_prestador)
        ON DELETE RESTRICT,
    CONSTRAINT fk_beneficio_nivel
        FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Beneficios canjeables con puntos';



-- movimiento_puntos: historial de puntos. Signo de cantidad según tipo (regla 5, se verifica con la fila) id_reserva solo existe en las
-- acumulaciones y es obligatorio en ellas como solo las acumulaciones la tienen, el UNIQUE garantiza "una reserva acumula una sola vez"
CREATE TABLE movimiento_puntos (
    id_movimiento  BIGINT AUTO_INCREMENT,
    fecha          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cantidad       INT         NOT NULL,
    tipo           VARCHAR(15) NOT NULL,
    id_membresia   INT NOT NULL,
    id_reserva     INT NULL,
    CONSTRAINT pk_movimiento PRIMARY KEY (id_movimiento),
    CONSTRAINT uq_movimiento_reserva UNIQUE (id_reserva),
    CONSTRAINT ck_movimiento_tipo
        CHECK (tipo IN ('acumulación', 'redención', 'vencimiento', 'ajuste manual')),
    CONSTRAINT ck_movimiento_signo
        CHECK (   (tipo = 'acumulación' AND cantidad > 0)
               OR (tipo IN ('redención', 'vencimiento') AND cantidad < 0)
               OR (tipo = 'ajuste manual' AND cantidad <> 0)),
    CONSTRAINT ck_movimiento_reserva
        CHECK (   (tipo = 'acumulación' AND id_reserva IS NOT NULL)
               OR (tipo <> 'acumulación' AND id_reserva IS NULL)),
    CONSTRAINT fk_movimiento_membresia
        FOREIGN KEY (id_membresia) REFERENCES membresia (id_membresia)
        ON DELETE RESTRICT,
    CONSTRAINT fk_movimiento_reserva
        FOREIGN KEY (id_reserva) REFERENCES reserva (id_reserva)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Historial de movimientos de puntos';




-- redencion: especialización del movimiento de tipo redención. Existe solo mientras exista el movimiento (CASCADE). El costo con que se
-- canjeó queda en movimiento_puntos.cantidad 
CREATE TABLE redencion (
    id_movimiento  BIGINT NOT NULL,
    id_beneficio   INT    NOT NULL,
    CONSTRAINT pk_redencion PRIMARY KEY (id_movimiento),
    CONSTRAINT fk_redencion_movimiento
        FOREIGN KEY (id_movimiento) REFERENCES movimiento_puntos (id_movimiento)
        ON DELETE CASCADE,
    CONSTRAINT fk_redencion_beneficio
        FOREIGN KEY (id_beneficio) REFERENCES beneficio (id_beneficio)
        ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Canje de un beneficio (subtipo de movimiento)';



-- 3. ÍNDICES DE APOYO para los reportes y las consultas frecuentes

CREATE INDEX ix_prestador_municipio ON prestador (id_municipio);
CREATE INDEX ix_reserva_cliente     ON reserva (id_cliente);
CREATE INDEX ix_reserva_plan        ON reserva (id_plan);
CREATE INDEX ix_reserva_fecha       ON reserva (fecha_servicio);
CREATE INDEX ix_pago_reserva        ON pago (id_reserva);
CREATE INDEX ix_notificacion_cliente ON notificacion (id_cliente, estado);
CREATE INDEX ix_movimiento_membresia ON movimiento_puntos (id_membresia, fecha);



-- 4. VISTAS


-- Aliado de cada movimiento: por la reserva si es acumulación (reserva -> plan -> prestador) y por el beneficio si es redención. Sirve para
-- validar las reglas 2 y 4 sin guardar el aliado en el movimiento
CREATE VIEW v_movimiento_aliado AS
SELECT m.id_movimiento,
       m.tipo,
       m.cantidad,
       m.id_membresia,
       pl.id_prestador AS id_prestador_aliado
FROM movimiento_puntos m
JOIN reserva r ON r.id_reserva = m.id_reserva
JOIN plan pl   ON pl.id_plan = r.id_plan
WHERE m.tipo = 'acumulación'
UNION ALL
SELECT m.id_movimiento,
       m.tipo,
       m.cantidad,
       m.id_membresia,
       b.id_prestador_aliado
FROM movimiento_puntos m
JOIN redencion rd ON rd.id_movimiento = m.id_movimiento
JOIN beneficio b  ON b.id_beneficio = rd.id_beneficio;

-- Saldo de puntos por membresía: es la suma de todos sus movimientos
CREATE VIEW v_saldo_membresia AS
SELECT mb.id_membresia,
       mb.id_cliente,
       COALESCE(SUM(m.cantidad), 0) AS saldo
FROM membresia mb
LEFT JOIN movimiento_puntos m ON m.id_membresia = mb.id_membresia
GROUP BY mb.id_membresia, mb.id_cliente;


