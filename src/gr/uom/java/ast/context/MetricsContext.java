package gr.uom.java.ast.context;

import gr.uom.java.ast.*;

/**
 * Encapsulates structural and software metrics for a single class.
 * Extracts what can be computed directly from JDeodorant's ClassObject.
 */
public class MetricsContext {

    private int loc;        // Lines of Code (approx. from method bodies)
    private int nom;        // Number of Methods
    private int noc;        // Number of Children
    private double wmc;     // Weighted Methods per Class
    private double cbo;     // Coupling Between Objects
    private double rfc;     // Response For a Class
    private double lcom;    // Lack of Cohesion in Methods

    private int fanIn;      // Classes depending on this class
    private int fanOut;     // Classes this class depends on

    public MetricsContext(ClassObject classObject) {
        if (classObject == null) return;

        // --- Simple metrics directly available ---
        this.nom = classObject.getNumberOfMethods();
        this.noc = countSubclasses(classObject);
        this.loc = 0;
        this.wmc = this.nom; // placeholder until complexity is computed

        // --- Derived placeholders (to be computed later) ---
        this.cbo = 0.0;
        this.rfc = 0.0;
        this.lcom = 0.0;

        this.fanIn = 0;
        this.fanOut = 0;
    }

    // Count subclasses of this class within the same system (optional heuristic)
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

    // --- Getters and setters ---
    public int getLoc() { return loc; }
    public int getNom() { return nom; }
    public int getNoc() { return noc; }
    public double getWmc() { return wmc; }
    public double getCbo() { return cbo; }
    public double getRfc() { return rfc; }
    public double getLcom() { return lcom; }
    public int getFanIn() { return fanIn; }
    public int getFanOut() { return fanOut; }

    public void setFanIn(int fanIn) { this.fanIn = fanIn; }
    public void setFanOut(int fanOut) { this.fanOut = fanOut; }

    @Override
    public String toString() {
        return "MetricsContext{" +
                "loc=" + loc +
                ", nom=" + nom +
                ", noc=" + noc +
                ", wmc=" + wmc +
                ", cbo=" + cbo +
                ", rfc=" + rfc +
                ", lcom=" + lcom +
                ", fanIn=" + fanIn +
                ", fanOut=" + fanOut +
                '}';
    }
}
