/*******************************************************************************
 * Copyright (c) 2007, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Mickael Istria (Red Hat Inc.) - [525882] Delete nested projects
 *******************************************************************************/
package org.eclipse.ltk.ui.refactoring.resource;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.osgi.util.NLS;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.LayoutConstants;
import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ltk.core.refactoring.participants.DeleteRefactoring;
import org.eclipse.ltk.internal.core.refactoring.Resources;
import org.eclipse.ltk.internal.core.refactoring.resource.DeleteResourcesProcessor;
import org.eclipse.ltk.internal.ui.refactoring.BasicElementLabels;
import org.eclipse.ltk.internal.ui.refactoring.Messages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIPlugin;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

/**
 * A wizard for the delete resources refactoring.
 *
 * @since 3.4
 */
public class DeleteResourcesWizard extends RefactoringWizard {

	/**
	 * Creates a {@link DeleteResourcesWizard}
	 *
	 * @param resources the resources to delete
	 */
	public DeleteResourcesWizard(IResource[] resources) {
		super(new DeleteRefactoring(new DeleteResourcesProcessor(resources, false)), DIALOG_BASED_USER_INTERFACE);
		setDefaultPageTitle(RefactoringUIMessages.DeleteResourcesWizard_page_title);
		setWindowTitle(RefactoringUIMessages.DeleteResourcesWizard_window_title);
	}

	@Override
	protected void addUserInputPages() {
		DeleteResourcesProcessor processor= getRefactoring().getAdapter(DeleteResourcesProcessor.class);
		addPage(new DeleteResourcesRefactoringConfigurationPage(processor));
	}

	private static class DeleteResourcesRefactoringConfigurationPage extends UserInputWizardPage {

		private DeleteResourcesProcessor fRefactoringProcessor;

		private Button fDeleteContentsButton;

		private StyledText fProjectLocationsList;

		private Label fProjectLocationsLabel;

		public DeleteResourcesRefactoringConfigurationPage(DeleteResourcesProcessor processor) {
			super("DeleteResourcesRefactoringConfigurationPage"); //$NON-NLS-1$
			fRefactoringProcessor= processor;
		}

		@Override
		public void createControl(Composite parent) {
			initializeDialogUnits(parent);

			Point defaultSpacing= LayoutConstants.getSpacing();

			Composite composite= new Composite(parent, SWT.NONE);
			GridLayout gridLayout= new GridLayout(2, false);
			gridLayout.horizontalSpacing= defaultSpacing.x * 2;
			gridLayout.verticalSpacing= defaultSpacing.y;

			composite.setLayout(gridLayout);
			composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Image image= parent.getDisplay().getSystemImage(SWT.ICON_QUESTION);
			Label imageLabel = new Label(composite, SWT.NULL);
			if (image != null) {
				imageLabel.setBackground(image.getBackground());
				imageLabel.setImage(image);
			}
			imageLabel.setLayoutData(new GridData(SWT.CENTER, SWT.BEGINNING, false, false));

			final IResource[] initialResources= fRefactoringProcessor.getResourcesToDelete();
			Label label= new Label(composite, SWT.WRAP);

			boolean onlyProjects= Resources.containsOnlyProjects(initialResources);
			if (onlyProjects) {
				if (initialResources.length == 1) {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_single_project, BasicElementLabels.getResourceName(initialResources[0])));
				} else {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_multi_projects, new Integer(initialResources.length)));
				}
			} else if (containsLinkedResource(initialResources)) {
				if (initialResources.length == 1) {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_single_linked, BasicElementLabels.getResourceName(initialResources[0])));
				} else {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_multi_linked, new Integer(initialResources.length)));
				}
			} else {
				if (initialResources.length == 1) {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_single, BasicElementLabels.getResourceName(initialResources[0])));
				} else {
					label.setText(Messages.format(RefactoringUIMessages.DeleteResourcesWizard_label_multi, new Integer(initialResources.length)));
				}
			}
			GridData gridData= new GridData(SWT.FILL, SWT.FILL, true, false);
			gridData.widthHint= convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
			label.setLayoutData(gridData);

			Composite supportArea= new Composite(composite, SWT.NONE);
			supportArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
			gridLayout= new GridLayout(1, false);
			gridLayout.horizontalSpacing= defaultSpacing.x * 2;
			gridLayout.verticalSpacing= defaultSpacing.y;

			supportArea.setLayout(gridLayout);

			if (onlyProjects) {
				Set<IProject> nestedProjects = computeNestedProjects(initialResources);
				fDeleteContentsButton= new Button(supportArea, SWT.CHECK);
				fDeleteContentsButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
				fDeleteContentsButton.setText(RefactoringUIMessages.DeleteResourcesWizard_project_deleteContents);
				fDeleteContentsButton.setFocus();

				if (!nestedProjects.isEmpty()) {
					Set<IResource> projectHierarchy = new HashSet<>(initialResources.length + nestedProjects.size(), (float)1.0);
					projectHierarchy.addAll(Arrays.asList(fRefactoringProcessor.getResourcesToDelete()));
					projectHierarchy.addAll(nestedProjects);
					Button deleteNestedProjectsCheckbox = new Button(supportArea, SWT.CHECK);
					deleteNestedProjectsCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
					deleteNestedProjectsCheckbox.setText(
							nestedProjects.size() == 1 ?
									RefactoringUIMessages.DeleteResourcesWizard_label_alsoDeleteOneNestedProject :
									NLS.bind(RefactoringUIMessages.DeleteResourcesWizard_label_alsoDeleteNestedProjects, nestedProjects.size()));
					SelectionAdapter deleteNestedProjectsCheckboxListener = new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							final boolean deleteNestedProjects = deleteNestedProjectsCheckbox.getSelection();
							try {
								getContainer().run(false, true, pm -> {
									try {
										fRefactoringProcessor.setResources(deleteNestedProjects ? projectHierarchy.toArray(new IResource[projectHierarchy.size()]) : initialResources, pm);
									} catch (OperationCanceledException | CoreException ex) {
										throw new InvocationTargetException(ex);
									}
								});
								updateListOfProjects();
							} catch (InvocationTargetException | InterruptedException ex) {
								RefactoringUIPlugin.log(ex);
							}
						}
					};
					deleteNestedProjectsCheckbox.addSelectionListener(deleteNestedProjectsCheckboxListener);

					fDeleteContentsButton.addSelectionListener(new SelectionAdapter() {
						private boolean previousNestedProjectSelection;

						@Override
						public void widgetSelected(SelectionEvent e) {
							if (fDeleteContentsButton.getSelection()) {
								previousNestedProjectSelection = deleteNestedProjectsCheckbox.getSelection();
								deleteNestedProjectsCheckbox.setSelection(true);
							} else {
								deleteNestedProjectsCheckbox.setSelection(previousNestedProjectSelection);
							}
							deleteNestedProjectsCheckbox.setEnabled(!fDeleteContentsButton.getSelection());
							super.widgetSelected(e);
						}
					});
					fDeleteContentsButton.addSelectionListener(deleteNestedProjectsCheckboxListener);
				}

				fProjectLocationsLabel= new Label(supportArea, SWT.NONE);
				GridData labelData= new GridData(SWT.FILL, SWT.FILL, true, false);
				labelData.verticalIndent= 5;
				fProjectLocationsLabel.setLayoutData(labelData);

				int style= SWT.MULTI | SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL;
				if (initialResources.length > 1 || !nestedProjects.isEmpty())
					style |= SWT.BORDER;
				fProjectLocationsList= new StyledText(supportArea, style);
				fProjectLocationsList.setAlwaysShowScrollBars(false);
				labelData.horizontalIndent= fProjectLocationsList.getLeftMargin();
				gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
				gridData.heightHint= convertHeightInCharsToPixels(Math.min(initialResources.length + nestedProjects.size(), 5));
				fProjectLocationsList.setLayoutData(gridData);
				fProjectLocationsList.setBackground(fProjectLocationsList.getDisplay().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
				Dialog.applyDialogFont(fProjectLocationsList);

				updateListOfProjects();
			}
			setControl(composite);
		}

		private void updateListOfProjects() {
			IResource[] initialResources = fRefactoringProcessor.getResourcesToDelete();
			StringBuffer buf= new StringBuffer();
			for (int i= 0; i < initialResources.length; i++) {
				String location= getLocation(initialResources[i]);
				if (location != null) {
					if (buf.length() > 0)
						buf.append('\n');
					buf.append(location);
				}
			}
			fProjectLocationsList.setText(buf.toString());
			fProjectLocationsLabel.setText(initialResources.length == 1
					? RefactoringUIMessages.DeleteResourcesWizard_project_location
					: NLS.bind(RefactoringUIMessages.DeleteResourcesWizard_project_locations, initialResources.length));
			fProjectLocationsList.getParent().pack(true);
			fProjectLocationsList.getParent().requestLayout();
		}

		private static String getLocation(IResource resource) {
			IPath location= resource.getLocation();
			if (location != null)
				return BasicElementLabels.getPathLabel(location, true);

			URI uri= resource.getLocationURI();
			if (uri != null)
				return BasicElementLabels.getURLPart(uri.toString());

			URI rawLocationURI= resource.getRawLocationURI();
			if (rawLocationURI != null)
				return BasicElementLabels.getURLPart(rawLocationURI.toString());

			return BasicElementLabels.getResourceName(resource);
		}

		private boolean containsLinkedResource(IResource[] resources) {
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				if (resource != null && resource.isLinked()) { // paranoia code, can not be null
					return true;
				}
			}
			return false;
		}

		@Override
		protected boolean performFinish() {
			initializeRefactoring();
			storeSettings();
			return super.performFinish();
		}

		@Override
		public IWizardPage getNextPage() {
			initializeRefactoring();
			storeSettings();
			return super.getNextPage();
		}

		private void initializeRefactoring() {
			fRefactoringProcessor.setDeleteContents(fDeleteContentsButton == null ? false : fDeleteContentsButton.getSelection());
		}

		private void storeSettings() {
		}
	}

	private static Set<IProject> computeNestedProjects(final IResource[] initialResources) {
		if (initialResources == null) {
			return Collections.emptySet();
		}
		final List<IResource> resources = new ArrayList<>(Arrays.asList(initialResources));
		resources.removeIf(res -> res.getLocation() == null);
		final Comparator<IResource> pathComparator = (res1, res2) -> res1.getLocation().toString().compareTo(res2.getLocation().toString());
		resources.sort(pathComparator);
		if (resources.isEmpty()) {
			return Collections.emptySet();
		}
		// need to be sorted
		final List<IProject> allProjects = new ArrayList<>(Arrays.asList(initialResources[0].getWorkspace().getRoot().getProjects()));
		allProjects.removeIf(project -> project.getLocation() == null);
		allProjects.sort(pathComparator);
		int resourceIndex = 0;
		int projectIndex = 0;
		final Set<IProject> res = new HashSet<>();
		while (resourceIndex < resources.size() && projectIndex < allProjects.size()) {
			final IPath resourcePath = resources.get(resourceIndex).getLocation();
			final IProject project = allProjects.get(projectIndex);
			final IPath projectPath = project.getLocation();
			if (resourcePath.isPrefixOf(projectPath)) {
				res.add(project);
				projectIndex++;
				continue;
			}
			int delta = resourcePath.toString().compareTo(projectPath.toString());
			if (delta < 0) {
				resourceIndex++;
			} else {
				projectIndex++;
			}
		}
		res.removeAll(resources);
		return res;
	}
}
