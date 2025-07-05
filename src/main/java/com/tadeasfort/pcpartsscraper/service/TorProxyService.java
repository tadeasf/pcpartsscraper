package com.tadeasfort.pcpartsscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for managing Tor proxy connections and rotation
 */
@Service
@Slf4j
public class TorProxyService {

    @Value("${app.tor.enabled:false}")
    private boolean torEnabled;

    @Value("${app.tor.socks-port:9050}")
    private int torSocksPort;

    @Value("${app.tor.control-port:9051}")
    private int torControlPort;

    @Value("${app.tor.host:127.0.0.1}")
    private String torHost;

    @Value("${app.tor.rotation-interval:10}")
    private int rotationInterval; // requests before rotation

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final List<Proxy> proxyList = new ArrayList<>();
    private final AtomicInteger currentProxyIndex = new AtomicInteger(0);

    /**
     * Initialize Tor proxy connections
     */
    public void initializeProxies() {
        if (!torEnabled) {
            log.info("Tor proxy is disabled");
            return;
        }

        try {
            // For now, we'll use a single Tor SOCKS proxy
            // In production, you might want to run multiple Tor instances on different
            // ports
            Proxy torProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(torHost, torSocksPort));
            proxyList.add(torProxy);

            log.info("Initialized {} Tor proxy connections", proxyList.size());
        } catch (Exception e) {
            log.error("Failed to initialize Tor proxies: {}", e.getMessage());
            torEnabled = false;
        }
    }

    /**
     * Get a Tor proxy for scraping requests
     * 
     * @return Proxy object or null if Tor is disabled
     */
    public Proxy getTorProxy() {
        if (!torEnabled || proxyList.isEmpty()) {
            return null;
        }

        // Rotate proxy every N requests
        int currentCount = requestCount.incrementAndGet();
        if (currentCount % rotationInterval == 0) {
            rotateProxy();
        }

        int index = currentProxyIndex.get() % proxyList.size();
        return proxyList.get(index);
    }

    /**
     * Rotate to next proxy and request new Tor circuit
     */
    private void rotateProxy() {
        if (!torEnabled) {
            return;
        }

        try {
            // Request new Tor circuit
            requestNewCircuit();

            // Move to next proxy (if we had multiple)
            currentProxyIndex.updateAndGet(i -> (i + 1) % Math.max(1, proxyList.size()));

            log.debug("Rotated Tor proxy, new circuit requested");
        } catch (Exception e) {
            log.warn("Failed to rotate Tor proxy: {}", e.getMessage());
        }
    }

    /**
     * Request a new Tor circuit by sending NEWNYM signal
     */
    private void requestNewCircuit() {
        try {
            // In a full implementation, you would connect to Tor's control port
            // and send a NEWNYM signal to get a new circuit
            // For now, we'll just log the action
            log.debug("Requesting new Tor circuit (NEWNYM signal)");

            // Example implementation would be:
            // Socket socket = new Socket(torHost, torControlPort);
            // PrintWriter writer = new PrintWriter(socket.getOutputStream());
            // writer.println("AUTHENTICATE");
            // writer.println("SIGNAL NEWNYM");
            // writer.close();
            // socket.close();

        } catch (Exception e) {
            log.warn("Failed to request new Tor circuit: {}", e.getMessage());
        }
    }

    /**
     * Check if Tor proxy is enabled and available
     * 
     * @return true if Tor is enabled and proxies are available
     */
    public boolean isTorEnabled() {
        return torEnabled && !proxyList.isEmpty();
    }

    /**
     * Get current proxy statistics
     * 
     * @return proxy usage statistics
     */
    public String getProxyStats() {
        if (!torEnabled) {
            return "Tor proxy: DISABLED";
        }

        return String.format("Tor proxy: ENABLED | Requests: %d | Current proxy: %d/%d",
                requestCount.get(),
                currentProxyIndex.get() + 1,
                proxyList.size());
    }
}