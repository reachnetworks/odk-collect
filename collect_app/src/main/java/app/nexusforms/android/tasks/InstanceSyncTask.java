/*
 * Copyright (C) 2017 Nyoman Ribeka
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package app.nexusforms.android.tasks;

import android.net.Uri;
import android.os.AsyncTask;

import org.apache.commons.io.FileUtils;
import app.nexusforms.android.R;

import app.nexusforms.android.analytics.AnalyticsEvents;
import app.nexusforms.android.application.Collect;
import app.nexusforms.android.database.DatabaseFormsRepository;
import app.nexusforms.android.database.DatabaseInstancesRepository;
import app.nexusforms.android.exception.EncryptionException;
import app.nexusforms.android.injection.DaggerUtils;
import app.nexusforms.android.instancemanagement.InstanceDeleter;
import app.nexusforms.android.instances.Instance;
import app.nexusforms.android.javarosawrapper.FormController;
import app.nexusforms.android.listeners.DiskSyncListener;
import app.nexusforms.android.preferences.keys.GeneralKeys;
import app.nexusforms.android.preferences.source.SettingsProvider;
import app.nexusforms.android.provider.InstanceProviderAPI;
import app.nexusforms.android.storage.StoragePathProvider;
import app.nexusforms.android.storage.StorageSubdirectory;
import app.nexusforms.android.utilities.EncryptionUtils;
import app.nexusforms.android.utilities.TranslationHandler;
import app.nexusforms.android.forms.Form;

import app.nexusforms.android.preferences.source.SettingsProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import timber.log.Timber;

import static app.nexusforms.android.utilities.FileUtils.getMd5Hash;

/**
 * Background task for syncing form instances from the instances folder to the instances table.
 * Returns immediately if it detects an error.
 */
public class InstanceSyncTask extends AsyncTask<Void, String, String> {

    private static int counter;

    private String currentStatus = "";
    private DiskSyncListener diskSyncListener;
    private final SettingsProvider settingsProvider;
    StoragePathProvider storagePathProvider = new StoragePathProvider();

    public String getStatusMessage() {
        return currentStatus;
    }

    public void setDiskSyncListener(DiskSyncListener diskSyncListener) {
        this.diskSyncListener = diskSyncListener;
    }

    public InstanceSyncTask(SettingsProvider settingsProvider) {
        this.settingsProvider = settingsProvider;
    }

    @Override
    protected String doInBackground(Void... params) {
        int currentInstance = ++counter;
        Timber.i("[%d] doInBackground begins!", currentInstance);
        try {
            List<String> candidateInstances = new LinkedList<>();
            File instancesPath = new File(storagePathProvider.getOdkDirPath(StorageSubdirectory.INSTANCES));
            if (instancesPath.exists() && instancesPath.isDirectory()) {
                File[] instanceFolders = instancesPath.listFiles();
                if (instanceFolders == null || instanceFolders.length == 0) {
                    Timber.i("[%d] Empty instance folder. Stopping scan process.", currentInstance);
                    Timber.d("Instance scan completed");
                    return currentStatus;
                }

                // Build the list of potential path that we need to add to the content provider
                for (File instanceDir : instanceFolders) {
                    File instanceFile = new File(instanceDir, instanceDir.getName() + ".xml");
                    if (!instanceFile.exists()) {
                        // Look for submission file that might have been manually copied from e.g. Briefcase
                        File submissionFile = new File(instanceDir, "submission.xml");
                        if (submissionFile.exists()) {
                            submissionFile.renameTo(instanceFile);
                        }
                    }
                    if (instanceFile.exists() && instanceFile.canRead()) {
                        candidateInstances.add(instanceFile.getAbsolutePath());
                    } else {
                        Timber.i("[%d] Ignoring: %s", currentInstance, instanceDir.getAbsolutePath());
                    }
                }
                Collections.sort(candidateInstances);

                List<Instance> instancesToRemove = new ArrayList<>();

                // Remove all the path that's already in the content provider
                List<Instance> instances = new DatabaseInstancesRepository().getAllNotDeleted();

                for (Instance instance : instances) {
                    String instanceFilename = storagePathProvider.getAbsoluteInstanceFilePath(instance.getInstanceFilePath());

                    if (candidateInstances.contains(instanceFilename) || instance.getStatus().equals(Instance.STATUS_SUBMITTED)) {
                        candidateInstances.remove(instanceFilename);
                    } else {
                        instancesToRemove.add(instance);
                    }
                }

                for (Instance instance : instancesToRemove) {
                    new InstanceDeleter(new DatabaseInstancesRepository(), new DatabaseFormsRepository()).delete(instance.getId());
                }

                final boolean instanceSyncFlag = settingsProvider.getGeneralSettings().getBoolean(GeneralKeys.KEY_INSTANCE_SYNC);

                int counter = 0;
                // Begin parsing and add them to the content provider
                for (String candidateInstance : candidateInstances) {
                    String instanceFormId = getFormIdFromInstance(candidateInstance);
                    // only process if we can find the id from the instance file
                    if (instanceFormId != null) {
                        try {
                            // TODO: optimize this by caching the previously found form definition
                            // TODO: optimize this by caching unavailable form definition to skip
                            List<Form> forms = new DatabaseFormsRepository().getAllByFormId(instanceFormId);

                            if (!forms.isEmpty()) {
                                Form form = forms.get(0);
                                String jrFormId = form.getJrFormId();
                                String jrVersion = form.getJrVersion();
                                String formName = form.getDisplayName();
                                String submissionUri = form.getSubmissionUri();

                                Instance instance = new DatabaseInstancesRepository().save(new Instance.Builder()
                                        .instanceFilePath(storagePathProvider.getRelativeInstancePath(candidateInstance))
                                        .submissionUri(submissionUri)
                                        .displayName(formName)
                                        .jrFormId(jrFormId)
                                        .jrVersion(jrVersion)
                                        .status(instanceSyncFlag ? Instance.STATUS_COMPLETE : Instance.STATUS_INCOMPLETE)
                                        .canEditWhenComplete(true)
                                        .build()
                                );
                                counter++;

                                encryptInstanceIfNeeded(form, instance);
                            }
                        } catch (IOException | EncryptionException e) {
                            Timber.w(e);
                        }
                    }
                }
                if (counter > 0) {
                    currentStatus += TranslationHandler.getString(Collect.getInstance(), R.string.instance_scan_count, counter);
                }
            }
        } finally {
            Timber.i("[%d] doInBackground ends!", currentInstance);
        }
        return currentStatus;
    }

    private String getFormIdFromInstance(final String instancePath) {
        String instanceFormId = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(instancePath));
            Element element = document.getDocumentElement();
            instanceFormId = element.getAttribute("id");
        } catch (Exception | Error e) {
            Timber.w("Unable to read form id from %s", instancePath);
        }
        return instanceFormId;
    }

    private String getInstanceIdFromInstance(final String instancePath) {
        String instanceId = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new File(instancePath));
            Element element = document.getDocumentElement();
            instanceId = element.getAttribute("instanceID");
        } catch (Exception | Error e) {
            Timber.w("Unable to read form instanceID from %s", instancePath);
        }
        return instanceId;
    }

    private void encryptInstanceIfNeeded(Form form, Instance instance) throws EncryptionException, IOException {
        if (instance != null) {
            if (shouldInstanceBeEncrypted(form)) {
                logImportAndEncrypt(form);
                encryptInstance(instance);
            }
        }
    }

    private void logImportAndEncrypt(Form form) {
        String id = form.getJrFormId();
        String title = form.getDisplayName();
        String formIdHash = app.nexusforms.android.utilities.FileUtils.getMd5Hash(new ByteArrayInputStream((id + " " + title).getBytes()));
        DaggerUtils.getComponent(Collect.getInstance()).analytics().logFormEvent(AnalyticsEvents.IMPORT_AND_ENCRYPT_INSTANCE, formIdHash);
    }

    private void encryptInstance(Instance instance)
            throws EncryptionException, IOException {

        String instancePath = storagePathProvider.getAbsoluteInstanceFilePath(instance.getInstanceFilePath());
        File instanceXml = new File(instancePath);
        if (!new File(instanceXml.getParentFile(), "submission.xml.enc").exists()) {
            Uri uri = Uri.parse(InstanceProviderAPI.InstanceColumns.CONTENT_URI + "/" + instance.getId());
            FormController.InstanceMetadata instanceMetadata = new FormController.InstanceMetadata(getInstanceIdFromInstance(instancePath), null, null);
            EncryptionUtils.EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(uri, instanceMetadata);

            if (formInfo != null) {
                File submissionXml = new File(instanceXml.getParentFile(), "submission.xml");
                FileUtils.copyFile(instanceXml, submissionXml);

                EncryptionUtils.generateEncryptedSubmission(instanceXml, submissionXml, formInfo);

                new DatabaseInstancesRepository().save(new Instance.Builder(instance)
                        .canEditWhenComplete(false)
                        .geometryType(null)
                        .geometry(null)
                        .build()
                );

                SaveFormToDisk.manageFilesAfterSavingEncryptedForm(instanceXml, submissionXml);
                if (!EncryptionUtils.deletePlaintextFiles(instanceXml, null)) {
                    Timber.e("Error deleting plaintext files for %s", instanceXml.getAbsolutePath());
                }
            }
        }
    }

    private boolean shouldInstanceBeEncrypted(Form form) {
        return form.getBASE64RSAPublicKey() != null;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (diskSyncListener != null) {
            diskSyncListener.syncComplete(result);
        }
    }
}