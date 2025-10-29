package gr.uom.java.ast.context;

import gr.uom.java.ast.*;
import gr.uom.java.ast.metrics.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates structural and software metrics for a single class.
 * Integrates JDeodorant metric subsystem (LCOM3, Connectivity, CBO)
 * and contextual fan-in / fan-out metrics.
 */
public class MetricsContext {

    private final int nom;          // Number of Methods
    private final int noc;          // Number of Children (subclasses)
    private final double cbo;       // Coupling Between Objects
    private final double lcom;      // Lack of Cohesion in Methods (LCOM3)
    private final double connectivity; // From ConnectivityMetric
    private int fanIn;              // Classes depending on this class
    private int fanOut;             // Classes this class depends on

    public MetricsContext(ClassObject classObject) {
        if (classObject == null) {
            this.nom = 0;
            this.noc = 0;
            this.cbo = 0.0;
            this.lcom = 0.0;
            this.connectivity = 0.0;
            return;
        }

        SystemObject system = ASTReader.getSystemObject();
        this.nom = classObject.getNumberOfMethods();
        this.noc = countSubclasses(classObject, system);

        double cboTmp = 0.0;
        double lcomTmp = 0.0;
        double connTmp = 0.0;

        try {
            // --- Compute metrics using JDeodorant metric classes ---
            LCOM lcomMetric = new LCOM(system);
            ConnectivityMetric connMetric = new ConnectivityMetric(system);
            MMImportCoupling couplingMetric = new MMImportCoupling(system);

            Map<String, Double> lcom3Map = getField(lcomMetric, "lcom3Map");
            Map<String, Double> connectivityMap = getField(connMetric, "classCohesionMap");
            Map<String, ?> importCouplingMap = getField(couplingMetric, "importCouplingMap");

            lcomTmp = safeGet(lcom3Map, classObject.getName());
            connTmp = safeGet(connectivityMap, classObject.getName());
            cboTmp = computeCBO(importCouplingMap, classObject.getName());
        } catch (Throwable t) {
            System.err.println("[MetricsContext] Warning: failed to compute full metrics for "
                    + classObject.getName() + " (" + t.getMessage() + ")");
        }

        this.cbo = cboTmp;
        this.lcom = lcomTmp;
        this.connectivity = connTmp;

        // fanIn/out set externally
        this.fanIn = 0;
        this.fanOut = 0;
    }

    // --- Reflection helpers ---
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

    private static double safeGet(Map<String, Double> map, String key) {
        if (map == null) return 0.0;
        Double val = map.get(key);
        return (val != null && !val.isNaN()) ? val : 0.0;
    }

    private int countSubclasses(ClassObject cls, SystemObject system) {
        if (system == null) return 0;
        int count = 0;
        for (ClassObject other : system.getClassObjects()) {
            TypeObject superType = other.getSuperclass();
            if (superType != null && superType.getClassType().equals(cls.getName())) {
                count++;
            }
        }
        return count;
    }

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

    // --- Fan-in/out setters ---
    public void setFanIn(int fanIn) { this.fanIn = fanIn; }
    public void setFanOut(int fanOut) { this.fanOut = fanOut; }

    // --- Getters ---
    public int getNom() { return nom; }
    public int getNoc() { return noc; }
    public double getCbo() { return cbo; }
    public double getLcom() { return lcom; }
    public double getConnectivity() { return connectivity; }
    public int getFanIn() { return fanIn; }
    public int getFanOut() { return fanOut; }

    @Override
    public String toString() {
        return "MetricsContext{" +
                "nom=" + nom +
                ", noc=" + noc +
                ", cbo=" + cbo +
                ", lcom=" + lcom +
                ", connectivity=" + connectivity +
                ", fanIn=" + fanIn +
                ", fanOut=" + fanOut +
                '}';
    }
}
