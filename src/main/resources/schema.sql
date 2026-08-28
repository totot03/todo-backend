-- M1: users 부분 유니크 인덱스 — 탈퇴 계정(deleted_at IS NOT NULL)의 이메일은
-- 유일성 검사에서 제외되어 같은 이메일로 재가입할 수 있다 (FR-A12, PRD 8.2).
-- 일반 UNIQUE 제약으로는 표현할 수 없어 부분 인덱스로 구현한다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

-- M1: todos 목록 조회 성능 인덱스 — 모든 조회가 user_id + deleted_at IS NULL 로 시작한다 (NFR-P02).
CREATE INDEX IF NOT EXISTS ix_todos_user_deleted
    ON todos (user_id, deleted_at);

-- M1: completed 필터가 포함된 목록 조회(예: 미완료만 보기)를 위한 복합 인덱스.
CREATE INDEX IF NOT EXISTS ix_todos_user_completed_deleted
    ON todos (user_id, completed, deleted_at);
