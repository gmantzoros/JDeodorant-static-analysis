package gr.uom.java.ast.context;

import java.util.List;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.Access;
import gr.uom.java.ast.CommentObject;
import java.util.ArrayList;
import java.util.ListIterator;

/**
 * A lightweight wrapper that captures contextual information about a field,
 * extracted from JDeodorant's FieldObject.
 */
public class FieldContext {
    private String name;
    private String type;
    private String declaringClass;
    private String visibility; // derived from Access
    private boolean isStatic;
    private List<String> comments = new ArrayList<>();

    public FieldContext(FieldObject field) {
        this.name = field.getName();
        this.type = field.getType() != null ? field.getType().toString() : "unknown";
        this.declaringClass = field.getClassName();
        this.visibility = accessToVisibility(field.getAccess());
        this.isStatic = field.isStatic();

        // Extract comment text if available
        ListIterator<CommentObject> it = field.getCommentListIterator();
        while (it.hasNext()) {
            CommentObject c = it.next();
            if (c != null && c.getText() != null) {
                comments.add(c.getText());
            }
        }
    }

    private String accessToVisibility(Access access) {
        if (access == null) return "default";
        switch (access) {
            case PUBLIC: return "public";
            case PRIVATE: return "private";
            case PROTECTED: return "protected";
            default: return "default";
        }
    }

    @Override
    public String toString() {
        return String.format(
            "%s %s %s (declared in %s)",
            visibility,
            isStatic ? "static" : "",
            type + " " + name,
            declaringClass
        ).trim();
    }

    // Getters and Setters
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDeclaringClass() {
		return declaringClass;
	}

	public void setDeclaringClass(String declaringClass) {
		this.declaringClass = declaringClass;
	}

	public String getVisibility() {
		return visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public boolean isStatic() {
		return isStatic;
	}

	public void setStatic(boolean isStatic) {
		this.isStatic = isStatic;
	}

	public List<String> getComments() {
		return comments;
	}

	public void setComments(List<String> comments) {
		this.comments = comments;
	}
}

