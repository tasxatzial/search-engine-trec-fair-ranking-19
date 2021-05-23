package gr.csd.uoc.hy463.themis.config.Exceptions;

/**
 * Thrown when loading the config file failed
 */
public class ConfigLoadException extends Exception {
    public ConfigLoadException() {
        super("Unable to load config");
    }
}
