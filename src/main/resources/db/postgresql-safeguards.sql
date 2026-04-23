ALTER TABLE service_requests
ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_service_requests_vehicle_active
ON service_requests (vehicle_id)
WHERE status IN ('OPEN', 'PENDING_APPROVAL', 'APPROVED', 'ASSIGNED', 'IN_PROGRESS');
