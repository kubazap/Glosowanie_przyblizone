package pl.zapala.projekt.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Protocol definitions for communication between Central Server and Satellite clients.
 * All messages are exchanged in JSON format.
 */
public class SatelliteProtocol {

    /**
     * Request types sent from Central Server to Satellites
     */
    public enum RequestType {
        GET_TIME,           // Request current time from satellite
        INJECT_CRASH,       // Simulate satellite crash
        INJECT_TIME_OFFSET, // Inject time offset error
        RESET_ERRORS,       // Reset all injected errors
        PING               // Health check
    }

    /**
     * Status of satellite response
     */
    public enum ResponseStatus {
        OK,
        ERROR,
        CRASHED
    }

    /**
     * Request message from Central Server to Satellite
     */
    public static class Request {
        private final RequestType type;
        private final Long parameter;

        @JsonCreator
        public Request(
                @JsonProperty("type") RequestType type,
                @JsonProperty("parameter") Long parameter) {
            this.type = type;
            this.parameter = parameter;
        }

        public RequestType getType() {
            return type;
        }

        public Long getParameter() {
            return parameter;
        }

        @Override
        public String toString() {
            return "Request{type=" + type + ", parameter=" + parameter + "}";
        }
    }

    /**
     * Response message from Satellite to Central Server
     */
    public static class Response {
        private final int id;
        private final long timestamp;
        private final ResponseStatus status;
        private final String message;

        @JsonCreator
        public Response(
                @JsonProperty("id") int id,
                @JsonProperty("timestamp") long timestamp,
                @JsonProperty("status") ResponseStatus status,
                @JsonProperty("message") String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.status = status;
            this.message = message;
        }

        public int getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ResponseStatus getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "Response{id=" + id + ", timestamp=" + timestamp +
                    ", status=" + status + ", message='" + message + "'}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Response response = (Response) o;
            return id == response.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * Satellite state information for the UI
     */
    public static class SatelliteState {
        private final int id;
        private final long lastSeenTime;
        private final long reportedTime;
        private final ResponseStatus status;
        private final double weight;
        private final boolean connected;

        public SatelliteState(int id, long lastSeenTime, long reportedTime,
                              ResponseStatus status, double weight, boolean connected) {
            this.id = id;
            this.lastSeenTime = lastSeenTime;
            this.reportedTime = reportedTime;
            this.status = status;
            this.weight = weight;
            this.connected = connected;
        }

        public int getId() {
            return id;
        }

        public long getLastSeenTime() {
            return lastSeenTime;
        }

        public long getReportedTime() {
            return reportedTime;
        }

        public ResponseStatus getStatus() {
            return status;
        }

        public double getWeight() {
            return weight;
        }

        public boolean isConnected() {
            return connected;
        }
    }
}