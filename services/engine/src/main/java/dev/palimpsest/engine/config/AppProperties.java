package dev.palimpsest.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed access to the palimpsest.* configuration (tokens, CORS, network cap). */
@ConfigurationProperties(prefix = "palimpsest")
public class AppProperties {

    private Auth auth = new Auth();
    private Cors cors = new Cors();
    private Network network = new Network();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    public static class Auth {
        private String scholarToken;
        private String pipelineToken;
        private String modelToken;
        private boolean requireAuthForReads;

        public String getScholarToken() {
            return scholarToken;
        }

        public void setScholarToken(String v) {
            this.scholarToken = v;
        }

        public String getPipelineToken() {
            return pipelineToken;
        }

        public void setPipelineToken(String v) {
            this.pipelineToken = v;
        }

        public String getModelToken() {
            return modelToken;
        }

        public void setModelToken(String v) {
            this.modelToken = v;
        }

        public boolean isRequireAuthForReads() {
            return requireAuthForReads;
        }

        public void setRequireAuthForReads(boolean v) {
            this.requireAuthForReads = v;
        }
    }

    public static class Cors {
        private String explorerOrigin = "http://localhost:5173";

        public String getExplorerOrigin() {
            return explorerOrigin;
        }

        public void setExplorerOrigin(String v) {
            this.explorerOrigin = v;
        }
    }

    public static class Network {
        private int edgeCap = 500;

        public int getEdgeCap() {
            return edgeCap;
        }

        public void setEdgeCap(int v) {
            this.edgeCap = v;
        }
    }
}
