CREATE TYPE job_status AS ENUM ('new', 'running', 'success', 'error');

CREATE TABLE jobs(
	id SERIAL, 
	payload bytea, 
	status job_status, 
	created_at timestamp, 
	updated_at timestamp
);

CREATE OR REPLACE FUNCTION jobs_status_notify()
	RETURNS trigger AS
$$
BEGIN
	PERFORM pg_notify('jobs_status_channel', NEW.id::text);
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER jobs_status
	AFTER INSERT OR UPDATE OF status
	ON jobs
	FOR EACH ROW
	WHEN (NEW.status::text <> 'error')
EXECUTE PROCEDURE jobs_status_notify();