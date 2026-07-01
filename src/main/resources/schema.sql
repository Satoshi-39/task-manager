CREATE TABLE IF NOT EXISTS users (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(50)   NOT NULL UNIQUE,
    password     VARCHAR(200)  NOT NULL,
    display_name VARCHAR(100)  NOT NULL,
    role         VARCHAR(20)   NOT NULL DEFAULT 'USER',
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP     NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_number      VARCHAR(30)   NOT NULL UNIQUE,
    title            VARCHAR(200)  NOT NULL,
    description      VARCHAR(2000),
    status           VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    priority         VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    due_date         TIMESTAMP,
    assigned_user_id BIGINT,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_tasks_user FOREIGN KEY (assigned_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_status   ON tasks(status);
CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(priority);
CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(due_date);
CREATE INDEX IF NOT EXISTS idx_tasks_assigned_user ON tasks(assigned_user_id);
