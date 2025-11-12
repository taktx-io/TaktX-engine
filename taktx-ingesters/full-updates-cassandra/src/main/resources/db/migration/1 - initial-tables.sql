
-- Full instance events (store raw update JSON plus kafka partition/offset for uniqueness)
CREATE TABLE IF NOT EXISTS taktx_instanceupdates.full_instance_events (
                                                          process_instance_id uuid,
                                                          kafka_partition int,
                                                          kafka_offset bigint,
                                                          event_timestamp bigint,
                                                          update_json text,
                                                          PRIMARY KEY ((process_instance_id), kafka_partition, kafka_offset)
    ) WITH CLUSTERING ORDER BY (kafka_partition ASC, kafka_offset ASC);

-- Variable updates keyed by instance and variable name (fast lookup by instance)
CREATE TABLE IF NOT EXISTS taktx_instanceupdates.variable_updates_by_instance_and_name (
                                                                           process_instance_id uuid,
                                                                           variable_name text,
                                                                           kafka_partition int,
                                                                           kafka_offset bigint,
                                                                           event_timestamp bigint,
                                                                           value_text text,
                                                                           PRIMARY KEY ((process_instance_id), variable_name, kafka_partition, kafka_offset)
    ) WITH CLUSTERING ORDER BY (variable_name ASC, kafka_partition ASC, kafka_offset ASC);

-- Variable updates indexed by name and value for reverse lookup (watch cardinality)
CREATE TABLE IF NOT EXISTS taktx_instanceupdates.variable_updates_by_name_value (
                                                                    variable_name text,
                                                                    value_text text,
                                                                    process_instance_id uuid,
                                                                    kafka_partition int,
                                                                    kafka_offset bigint,
                                                                    event_timestamp bigint,
                                                                    PRIMARY KEY ((variable_name, value_text), process_instance_id, kafka_partition, kafka_offset)
    ) WITH CLUSTERING ORDER BY (process_instance_id ASC, kafka_partition ASC, kafka_offset ASC);