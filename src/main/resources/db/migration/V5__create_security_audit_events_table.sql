CREATE TABLE security_audit_events (
    id BIGSERIAL PRIMARY KEY,
    outcome VARCHAR(20) NOT NULL,
    action VARCHAR(150) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    roles VARCHAR(300) NOT NULL,
    resource VARCHAR(150) NOT NULL,
    reason TEXT,
    request_id VARCHAR(128),
    details_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_audit_created_at ON security_audit_events(created_at DESC);
CREATE INDEX idx_security_audit_outcome_created ON security_audit_events(outcome, created_at DESC);
CREATE INDEX idx_security_audit_actor_created ON security_audit_events(actor, created_at DESC);
CREATE INDEX idx_security_audit_request_id ON security_audit_events(request_id);
