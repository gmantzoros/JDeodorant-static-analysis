package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.metrics.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates structural and software metrics for a single class.
 * Uses JDeodorant's metric subsystem: ConnectivityMetric, LCOM, and MMImportCoupling.
 */
public class MetricsContext {

    private int nom;        // Number of Methods
    private int noc;        // Number of Children
    private double wmc;     // Weighted Methods per Class (simplified)
    private double cbo;     // Coupling Between Objects
    private double lcom;    // Lack of Cohesion in Methods (LCOM3)
    private double connectivity; // From ConnectivityMetric
    private int fanIn;      // Classes depending on this class
    private int fanOut;     // Classes this class depends on

    public MetricsContext(ClassObject classObject) {
        if (classObject == null) return;

        SystemObject system = ASTReader.getSystemObject();
        this.nom = classObject.getNumberOfMethods();
        this.noc = countSubclasses(classObject);

        // Default WMC: number of methods (simple)
        this.wmc = nom;

        // --- Compute real metrics if possible ---
        try {
            // LCOM and Connectivity are class-level metrics
            LCOM lcomMetric = new LCOM(system);
            ConnectivityMetric connMetric = new ConnectivityMetric(system);
            MMImportCoupling couplingMetric = new MMImportCoupling(system);

            Map<String, Double> lcom3Map = getField(lcomMetric, "lcom3Map");
            Map<String, Double> connectivityMap = getField(connMetric, "classCohesionMap");
            Map<String, ?> importCouplingMap = getField(couplingMetric, "importCouplingMap");

            this.lcom = safeGet(lcom3Map, classObject.getName());
            this.connectivity = safeGet(connectivityMap, classObject.getName());
            this.cbo = computeCBO(importCouplingMap, classObject.getName());
        } catch (Throwable t) {
            // fallback if metrics cannot be computed
            this.lcom = 0.0;
            this.connectivity = 0.0;
            this.cbo = 0.0;
        }

        this.fanIn = 0;
        this.fanOut = 0;
    }

    // --- Utility to read private maps via reflection  ---
    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> getField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Map<String, T>) f.get(obj);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    // --- Safe getter with default ---
    private static double safeGet(Map<String, Double> map, String key) {
        if (map == null) return 0.0;
        Double val = map.get(key);
        return (val != null && !val.isNaN()) ? val : 0.0;
    }

    // --- Count subclasses in system ---
    private int countSubclasses(ClassObject cls) {
        int count = 0;
        SystemObject system = ASTReader.getSystemObject();
        if (system == null) return 0;
        for (ClassObject other : system.getClassObjects()) {
            TypeObject superType = other.getSuperclass();
            if (superType != null && superType.getClassType().equals(cls.getName())) {
                count++;
            }
        }
        return count;
    }

    // --- Compute average coupling (CBO) from MMImportCoupling ---
    @SuppressWarnings("unchecked")
    private double computeCBO(Map<String, ?> importCouplingMap, String className) {
        try {
            LinkedHashMap<String, Integer> map = (LinkedHashMap<String, Integer>) importCouplingMap.get(className);
            if (map == null) return 0.0;
            int sum = 0;
            int count = 0;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                if (!e.getKey().equals(className)) {
                    sum += e.getValue();
                    count++;
                }
            }
            return count == 0 ? 0.0 : (double) sum / count;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    // --- External fan-in/out setters ---
    public void setFanIn(int fanIn) { this.fanIn = fanIn; }
    public void setFanOut(int fanOut) { this.fanOut = fanOut; }

    // --- Getters ---
    public int getNom() { return nom; }
    public int getNoc() { return noc; }
    public double getWmc() { return wmc; }
    public double getCbo() { return cbo; }
    public double getLcom() { return lcom; }
    public double getConnectivity() { return connectivity; }
    public int getFanIn() { return fanIn; }
    public int getFanOut() { return fanOut; }

    @Override
    public String toString() {
        return "MetricsContext{" +
                ", nom=" + nom +
                ", noc=" + noc +
                ", wmc=" + wmc +
                ", cbo=" + cbo +
                ", lcom=" + lcom +
                ", connectivity=" + connectivity +
                ", fanIn=" + fanIn +
                ", fanOut=" + fanOut +
                '}';
    }
}
