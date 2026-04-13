CREATE TABLE IF NOT EXISTS symbol_maps
(
    id        SERIAL PRIMARY KEY,
    symbol    VARCHAR(72) NOT NULL,
    alias_key VARCHAR(72) NOT NULL,
    alias     VARCHAR(72) NOT NULL,
    UNIQUE (symbol, alias_key, alias)
);

CREATE TABLE IF NOT EXISTS api_key
(
    id                    SERIAL PRIMARY KEY,
    user_id               VARCHAR(36)  NOT NULL,
    label                 VARCHAR(200) NOT NULL,
    access_token          TEXT         NOT NULL,
    refresh_token         TEXT         NOT NULL,
    expiration_time       TIMESTAMP,
    allowed_ips           TEXT,
    token_expiration_time TIMESTAMP    NOT NULL,
    key                   VARCHAR(36)  NOT NULL UNIQUE,
    is_enabled            BOOLEAN      NOT NULL DEFAULT true,
    is_expired            BOOLEAN      NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS stop_orders
(
    id            BIGSERIAL PRIMARY KEY,
    uuid          VARCHAR(64)    NOT NULL,
    ouid          VARCHAR(128)   NOT NULL UNIQUE,
    symbol        VARCHAR(20)    NOT NULL,
    side          VARCHAR(4)     NOT NULL,
    quantity      NUMERIC(36,8)  NOT NULL,
    price         NUMERIC(36,8)  NOT NULL,
    stop_price    NUMERIC(36,8)  NOT NULL,
    time_in_force VARCHAR(5)     NOT NULL DEFAULT 'GTC',
    status        VARCHAR(20)    NOT NULL DEFAULT 'WAITING',
    create_date   TIMESTAMP      NOT NULL DEFAULT NOW()
);
