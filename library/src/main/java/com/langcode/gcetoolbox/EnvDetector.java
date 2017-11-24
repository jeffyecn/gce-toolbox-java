package com.langcode.gcetoolbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class EnvDetector {

    private final static Logger LOG = LoggerFactory.getLogger(EnvDetector.class);

    private final static EnvDetector instance = new EnvDetector();

    public static EnvDetector getInstance() {
        return instance;
    }

    private Timer timer = null;

    private EnvDetector() {

    }

    public void detect() {
        LOG.info("detect env");
    }

    public void enableAutoRefresh(long interval, TimeUnit timeUnit) {
        if ( timer != null ) {
            LOG.warn("auto refresh already enabled");
        }
        timer = new Timer("env refresh time", true);
        long period = timeUnit.toMillis(interval);
        LOG.info("period {}", period);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    detect();
                } catch (Exception ex) {
                    LOG.error("Refresh env failed.");
                }
            }
        }, period, period);
    }

    public boolean runningInGCE() {
        return false;
    }
}
