package dev.relay.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Logging for the server, deliberately just this.
 *
 * <p>A logging framework would be the first dependency in a process whose whole job is to hold a
 * few maps and copy bytes between sockets. Output goes to stdout; redirect it if you want a file.
 */
public final class Log {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	public static void info(String message) {
		print("INFO", message);
	}

	public static void warn(String message) {
		print("WARN", message);
	}

	public static void error(String message) {
		print("ERROR", message);
	}

	/** For the technical detail that should never be shown to a player. */
	public static void error(String message, Throwable cause) {
		print("ERROR", message + ": " + cause);
		cause.printStackTrace(System.out);
	}

	private static void print(String level, String message) {
		System.out.println("[" + LocalTime.now().format(TIME) + "] [" + level + "] " + message);
	}

	private Log() {
	}
}
