package io.seqware.pancancer;

import java.util.HashMap;
import java.util.Map;

import net.sourceforge.seqware.pipeline.workflowV2.AbstractWorkflowDataModel;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;

/**
 * <p>
 * For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare Java Workflows</a>.
 * </p>
 * 
 * Quick reference for the order of methods called: 1. setupDirectory 2. setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 * 
 * See the SeqWare API for <a href=
 * "http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">
 * AbstractWorkflowDataModel</a> for more information.
 */
public class BroadWrapperWorkflow extends AbstractWorkflowDataModel {

    private String workflowID;
    private String workflowDir;
    private String rsyncUrl;
    private String rsyncKey;
    private String largeWorkDir;
    private boolean checkWorkflowFileExists = false;

    private Map<String,String> workflowProperties  = new HashMap<String,String>(5);
    
    private void setMandatoryPropertyFromINI(String propKey) throws Exception
    {
        if (hasPropertyAndNotNull(propKey)){
            //this.workflowDir = getProperty(propKey);
            workflowProperties.put(propKey, getProperty(propKey));
        }
        else
        {
            throw new RuntimeException("\""+propKey+"\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
        }
    }
    
    private void init() {
        try {
            
            this.setMandatoryPropertyFromINI("workflow_dir");
            this.workflowDir = this.workflowProperties.get("workflow_dir");
            
            this.setMandatoryPropertyFromINI("workflow_id");
            this.workflowID = this.workflowProperties.get("workflow_id");

            this.setMandatoryPropertyFromINI("rsync_url");
            this.rsyncUrl = this.workflowProperties.get("rsync_url");
            
            this.setMandatoryPropertyFromINI("rsync_key");
            this.rsyncKey = this.workflowProperties.get("rsync_key");

            this.setMandatoryPropertyFromINI("large_work_dir");
            this.largeWorkDir = this.workflowProperties.get("large_work_dir");
            
            //This is optional - if the user doesn't specify, we'll skip the check.
            if (hasPropertyAndNotNull("check_workflowfile_exists")) {
                this.checkWorkflowFileExists = Boolean.valueOf( getProperty("check_workflowfile_exists") );
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Job setSynapseStatus(Job previousJob, String status, String workflowID)
    {
        Job setSynapseStatusJob = this.getWorkflow().createBashJob("set_synapse_status_"+status);
        
        setSynapseStatusJob.getCommand().addArgument("cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py set "+status+" "+workflowID);
        
        setSynapseStatusJob.addParent(previousJob);
        
        return setSynapseStatusJob;
    }
    
    @Override
    public void buildWorkflow() {
        this.init();
       
        // Generate workflow files. Yes, we are generating for ALL that are registered for the user with synapse credentials in this machine, but this
        // is not a very expensive step with no side-effects and the INI will specify exactly which INI to use.
        Job generateJobs = this.generateWorkflowFilesJob();

        // The Broad scripts may fail if the file does not exist, but they may
        // not return a non-zero error code, so this workflow will finish very quickly and *appear* successful when it is not.
        Job checkforWFFile = this.checkForWorkflowFile(this.workflowID, generateJobs);
        
        // Run the workflow.
        // It seems like a lot of Broad scripts will exit with code 0 even if they encountered an error (missing input file, HTTP timeout, etc...) and ended
        // so SeqWare might "succeed" even when Broad fails.
        Job runBroad = this.runBroadWorkflow(this.workflowID, checkforWFFile);

        // Post-Broad step that outputs the <WORKFLOWID>.err and <WORKFLOWID>.out files so that they are a part of the seqware output
        Job catBroad = this.catBroadLogs(this.workflowID, runBroad);
        
        // Post-Broad step to check the galaxy errors
        Job checkGalaxyErrs = this.checkGalaxyErrs(catBroad);
        
        // make upload scripts
        Job broadUploadPrep = this.prepareUpload(workflowID, this.rsyncUrl, this.rsyncKey, checkGalaxyErrs);
        
        // prep upload
        Job prep = this.doPrepShellScript(workflowID, broadUploadPrep);

        // actually do rsync upload
        Job upload = this.doUpload(workflowID,prep);
        
        // Now set the status to "complete". The workflow IS complete, assuming we got here without any errors or exceptions.
        @SuppressWarnings("unused")
        Job setStatusRunning = this.setSynapseStatus(upload, "complete", workflowID);

    }
    

    private Job generateWorkflowFilesJob()
    {
        // Copy the synapse config file
        Job copySynapseConfig = this.copyInSynapseConfig();
        
        Job generateWFFilesJob = this.getWorkflow().createBashJob("generate_broad_workflow_files");
        // The PCAWG tool scripts sometimes experience path confusion when they are called from a generated seqware datatore directory. So, we will
        // cd to $PCAWG_DIR, and then call the pcawg_wf_gen.py script.
        generateWFFilesJob.getCommand().addArgument("echo \"PYTHONPATH: $PYTHONPATH PCAWGDIR: $PCAWG_DIR NEBULA: $NEBULA\" && sudo mkdir -p /datastore/nebula/work && sudo chmod -R a+rwx /datastore/nebula && cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir "+this.largeWorkDir );
        generateWFFilesJob.addParent(copySynapseConfig);
        return generateWFFilesJob;
    }

    private Job checkForWorkflowFile(String workflowID, Job previousJob)
    {
        
        Job checkForWorkflowFileJob = this.getWorkflow().createBashJob("check_that_workflow_file_exists");
        // stat will terminate with exit code "1" if the file is not found.
        if (this.checkWorkflowFileExists)
            checkForWorkflowFileJob.getCommand().addArgument("stat "+this.workflowDir+"/workflow_"+workflowID);

        checkForWorkflowFileJob.addParent(previousJob);

        return checkForWorkflowFileJob;
    }
    
    /*
     * So... this workflow needs a .synapseConfig. This can come from the .gnos directory on the host machine, which will be mounted to the container.
     * BUT if a SeqWare step fails and the user tries to restart, they may need to do this step again because we are running SeqWare with docker containers
     * referencing /datastore, and the ~/.synapseConfig will need to be recreated when re-running a workflow from some step in the middle. 
     */
    private Job copyInSynapseConfig()
    {
        Job copySynapseConfigFile = this.getWorkflow().createBashJob("copy_synapse_config");
        
        //Before doing anything else, .synapseConfig needs to be copied from /home/ubuntu/.gnos to ~/.synapseConfig. it's an ugly hack, treating it like a GNOS key, but it should work.
        copySynapseConfigFile.getCommand().addArgument("cp /home/ubuntu/.gnos/.synapseConfig /home/seqware/.synapseConfig");
        
        return copySynapseConfigFile;
    }
    
    private Job runBroadWorkflow(String workflowID, Job previousJob)
    {
        // Copy the synapse config file
        Job copySynapseConfig = this.copyInSynapseConfig();
        copySynapseConfig.addParent(previousJob);
        
        // Now set the status to running.
        Job setStatusRunning = this.setSynapseStatus(copySynapseConfig, "running", workflowID);
        
        //Need to execute: qsub sge_qsub_runworkflow.sh pcawg_data.service pcawg_data.tasks/<workflow_id_fill_in>
        Job runBroadJob = this.getWorkflow().createBashJob("run_broad_workflow");

        runBroadJob.getCommand().addArgument("cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh pcawg_data.service "+this.workflowDir+"/workflow_"+workflowID + " || /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py set failed "+workflowID);
        runBroadJob.addParent(setStatusRunning);
        
        return runBroadJob;
    }

    private Job catBroadLogs(String workflowID, Job previousJob) {

        Job catBroadLogsJob = this.getWorkflow().createBashJob("cat_workflow_broad_logs");

        catBroadLogsJob.getCommand().addArgument("echo \"***** WORKFLOW_OUT_LOG *****\" && cat " + this.workflowDir + "/workflow_" + workflowID + ".out && echo \"***** WORKFLOW_ERR_LOG ***** \" && cat " + this.workflowDir + "/workflow_" + workflowID + ".err >&2  ");
        catBroadLogsJob.addParent(previousJob);

        return catBroadLogsJob;
    }

    private Job checkGalaxyErrs(Job previousJob) {
        Job checkGalaxyErrsJob = this.getWorkflow().createBashJob("cat_galaxy_errs");
        
        checkGalaxyErrsJob.getCommand().addArgument("cd $PCAWG_DIR && scripts/pcawg_wf_gen.py errors --full > galaxy.err && cat galaxy.err");
        checkGalaxyErrsJob.addParent(previousJob);
        return checkGalaxyErrsJob;
    }
    
    private Job prepareUpload(String workflowID, String rsyncURL, String rsyncKey, Job previousJob)
    {
        // Copy the synapse config file
        Job copySynapseConfig = this.copyInSynapseConfig();
        copySynapseConfig.addParent(previousJob);
        
        Job prepareUploadJob = this.getWorkflow().createBashJob("prepare_upload");
        prepareUploadJob.getCommand().addArgument("cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py upload-prep --rsync "+rsyncURL+" --rsync-key "+rsyncKey+" "+workflowID + " || /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py set failed "+workflowID);
        prepareUploadJob.addParent(copySynapseConfig);
        // TODO: Need to find a way to verify successful completion!
        return prepareUploadJob;
    }
    
    
    private Job doPrepShellScript(String workflowID, Job previousJob)
    {
        // Copy the synapse config file
        Job copySynapseConfig = this.copyInSynapseConfig();
        copySynapseConfig.addParent(previousJob);
        
        // First, check that the prep.sh script actually exists
        Job checkPrepScriptExists = this.getWorkflow().createBashJob("check_prep_sh_exists");
        // Mark as "failed" in Synapse if no prep.sh script was generated. Since some of the earlier steps may not propagate their error code correctly, 
        // we might be uncertain about errors until this step.
        checkPrepScriptExists.getCommand().addArgument("cd $PCAWG_DIR && stat upload/*/"+workflowID+"/*/prep.sh || /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py set failed "+workflowID);
        checkPrepScriptExists.addParent(copySynapseConfig);
        
        
        Job doPrepJob = this.getWorkflow().createBashJob("do_prep_sh");
        //Do ALL of the prep scripts
        doPrepJob.getCommand().addArgument("cd $PCAWG_DIR && for i in upload/*/"+workflowID+"/*/prep.sh; do bash $i; done; ");
        doPrepJob.addParent(checkPrepScriptExists);
        return doPrepJob;
    }
    
    private Job doUpload(String workflowID, Job previousJob)
    {
        // Copy the synapse config file
        Job copySynapseConfig = this.copyInSynapseConfig();
        copySynapseConfig.addParent(previousJob);
        
        // First, check that the upload.sh script actually exists
        Job checkUploadScriptExists = this.getWorkflow().createBashJob("check_upload_sh_exists");
        // Mark as "failed" in Synapse if no upload.sh script was generated. Since some of the earlier steps may not propagate their error code correctly, 
        // we might be uncertain about errors until this step.
        checkUploadScriptExists.getCommand().addArgument("cd $PCAWG_DIR && stat upload/*/"+workflowID+"/*/upload.sh || /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py set failed "+workflowID);
        checkUploadScriptExists.addParent(copySynapseConfig);

        
        Job doUploadJob = this.getWorkflow().createBashJob("do_upload_sh");
        //Do all of the uploads.
        doUploadJob.getCommand().addArgument("cd $PCAWG_DIR && for i in upload/*/"+workflowID+"/*/upload.sh; do bash $i; done; ");
        doUploadJob.addParent(checkUploadScriptExists);
        return doUploadJob;
    }
}
