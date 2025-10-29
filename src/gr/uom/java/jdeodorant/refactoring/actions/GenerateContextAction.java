package gr.uom.java.jdeodorant.refactoring.actions;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.*;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.*;
import gr.uom.java.ast.context.ContextBuilder;
import gr.uom.java.ast.context.ObjectContext;
import gr.uom.java.jdeodorant.refactoring.views.ContextView;

/**
 * Eclipse menu action that builds and displays the ObjectContext
 * for the currently selected Java project using ContextBuilder.
 */
public class GenerateContextAction implements IWorkbenchWindowActionDelegate {

    private IWorkbenchWindow window;

    @Override
    public void init(IWorkbenchWindow window) {
        this.window = window;
    }

    @Override
    public void run(IAction action) {
        if (window == null)
            return;

        try {
            ISelection selection = window.getSelectionService().getSelection();
            IProject project = getSelectedProject(selection);

            if (project == null) {
                MessageDialog.openInformation(window.getShell(), "Generate Context",
                        "Please select a Java project in the Package Explorer.");
                return;
            }

            IJavaProject javaProject = JavaCore.create(project);
            if (javaProject == null || !javaProject.exists()) {
                MessageDialog.openInformation(window.getShell(), "Generate Context",
                        "The selected project is not a valid Java project.");
                return;
            }

            System.out.println("========================================");
            System.out.println("[GenerateContextAction] Starting context generation for project: " + project.getName());
            System.out.println("========================================");

            // --- Build the project context ---
            ObjectContext context = ContextBuilder.buildCurrentProjectContext();
            if (context == null) {
                MessageDialog.openWarning(window.getShell(), "Generate Context",
                        "Failed to build ObjectContext for project: " + project.getName());
                return;
            }

            // --- Show the context in Eclipse view ---
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                try {
                    ContextView view = (ContextView) page.showView(ContextView.ID);
                    view.setObjectContext(context);
                } catch (PartInitException e) {
                    e.printStackTrace();
                    MessageDialog.openError(window.getShell(), "View Error",
                            "Could not open the Context View: " + e.getMessage());
                }
            }

            System.out.println("[GenerateContextAction] Context successfully generated and displayed for: " + project.getName());
            MessageDialog.openInformation(window.getShell(), "Generate Context",
                    "Context successfully built, and displayed!\n\nProject: " + project.getName());

        } catch (Exception e) {
            e.printStackTrace();
            MessageDialog.openError(window.getShell(), "Context Generation Error",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        // no-op
    }

    @Override
    public void dispose() {
        // no-op
    }

    /**
     * Resolves the currently selected project from different selection types.
     */
    private IProject getSelectedProject(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            Object first = ((IStructuredSelection) selection).getFirstElement();

            if (first instanceof IProject)
                return (IProject) first;

            if (first instanceof IJavaProject)
                return ((IJavaProject) first).getProject();

            if (first instanceof IJavaElement)
                return ((IJavaElement) first).getJavaProject().getProject();

            if (first instanceof IResource)
                return ((IResource) first).getProject();

            if (first instanceof IAdaptable) {
                IResource r = (IResource) ((IAdaptable) first).getAdapter(IResource.class);
                if (r != null)
                    return r.getProject();

                IJavaElement je = (IJavaElement) ((IAdaptable) first).getAdapter(IJavaElement.class);
                if (je != null)
                    return je.getJavaProject().getProject();
            }
        }
        return null;
    }
}
