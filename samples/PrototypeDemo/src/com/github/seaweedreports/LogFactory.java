package com.github.seaweedreports;
import org.apache.log4j.Logger;

public class LogFactory {
    public static Logger getLog() {
		final Throwable t = new Throwable();
		t.fillInStackTrace();
		return Logger.getLogger(t.getStackTrace()[1].getClassName());
	}

}
