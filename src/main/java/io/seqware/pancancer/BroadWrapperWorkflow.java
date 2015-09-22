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
    //private boolean cleanup;
    private boolean checkWorkflowFileExists = false;
    
    private String rsyncUrl;
    private String rsyncKey;

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
            this.workflowDir = this.workflowProperties.get("workflow_id");

            this.setMandatoryPropertyFromINI("rsync_url");
            this.workflowDir = this.workflowProperties.get("rsync_url");
            
            this.setMandatoryPropertyFromINI("rsync_key");
            this.workflowDir = this.workflowProperties.get("rsync_key");
            
            //This is optional - if the user doesn't specify, we'll skip the check.
            if (hasPropertyAndNotNull("check_workflowfile_exists")) {
                this.checkWorkflowFileExists = Boolean.valueOf( getProperty("check_workflowfile_exists") );
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    @Override
//    public void setupDirectory() {
//        // since setupDirectory is the first method run, we use it to initialize variables too.
//        init();
//        // creates a dir1 directory in the current working directory where the workflow runs
//        this.addDirectory(jobsDir);
//    }

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
        Job runBroad = this.runBroadWorkflow(this.workflowID, checkforWFFile);
        
        // TODO: Maybe  have a post-Broad step that outputs the <WORKFLOWID>.err and <WORKFLOWID>.out files so that they are a part of the seqware output?
        // It seems like a lot of Broad scripts will exit with code 0 even if they encountered an error (missing input file, HTTP timeout, etc...) and ended
        // so SeqWare might "succeed" even when Broad fails.        
        Job broadUploadPrep = this.prepareUpload(workflowID, this.rsyncUrl, this.rsyncKey, runBroad);
        
        Job prep = this.doPrepShellScript(workflowID, broadUploadPrep);
        
        @SuppressWarnings("unused")
        Job upload = this.doUpload(workflowID,prep);
    }
    
//    private void cleanupWorkflow(Job... lastJobs) {
//        Job cleanupJob = null;
//        if (cleanup) {
//            cleanupJob = this.getWorkflow().createBashJob("cleanup");
//            // Clean up the jobsDir 
//            cleanupJob.getCommand().addArgument("rm -Rf "+this.jobsDir+" \n");
//            // Should cleanup also kill/remove the nebula_galaxy container/other containers?
//        }
//        for (Job lastJob : lastJobs) {
//            if (lastJob != null && cleanupJob != null) {
//                cleanupJob.addParent(lastJob);
//            }
//        }
//    }
    
 
    private Job generateWorkflowFilesJob()
    {
        Job generateWFFilesJob = this.getWorkflow().createBashJob("generate_broad_workflow_files");
        // The PCAWG tool scripts sometimes experience path confusion when they are called from a generated seqware datatore directory. So, we will
        // spawn a subshell and cd to $PCAWG_DIR, and then call the pcawg_wf_gen.py script.
        generateWFFilesJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir "+this.workflowDir + " ) ");
        
        return generateWFFilesJob;
    }
    
    private Job runBroadWorkflow(String workflowID, Job previousJob)
    {
        //Need to execute: qsub sge_qsub_runworkflow.sh pcawg_data.service pcawg_data.tasks/<workflow_id_fill_in>
        Job runBroadJob = this.getWorkflow().createBashJob("run_broad_workflow");
        
        // This shouldn't need "sudo" but it won't run without it. :/ 
/*
        runBroadJob.getCommand().addArgument("sudo docker run --rm -h master"
                                                            // Mount the docker socket so that the container can call docker at the top-level
                                                            +" -v /var/run/docker.sock:/var/run/docker.sock "
                                                            // Mount the images directory. This directory contains the TAR files for all of the images necessary for running Broad 
                                                            +" -v /workflows/gitroot/pcawg_tools/images:/workflows/gitroot/pcawg_tools/images:ro "
                                                            // Mount the service config file - needs to be created when generating workflow files, before this step runs!
                                                            // NO LONGER NEEDED since workflow file generation now occurs inside the workflow.
                                                            //+" -v /workflows/gitroot/pcawg_tools/pcawg_data.service:/workflows/gitroot/pcawg_tools/pcawg_data.service:ro"
                                                            // Mount the datastore, datastore will need to contain "nebula/work"; not certain if nebula needs this already to exist, or if it just needs /datastore and will set up its own directories. 
                                                            +" -v /datastore:/datastore "
                                                            // Mount the directory with the work that needs to be done.
                                                            +" -v " + this.workflowDir+":/tasks "
                                                            + this.pcawgContainerName+" /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service /tasks/"+workflowID);
*/
        runBroadJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service  "+this.workflowDir+"/"+workflowID+" ) ");
        runBroadJob.addParent(previousJob);

//        // TODO: need parameters for this
//        // TODO: does the workflowID have workflow_... in it or just the donor ID?
//        Job prepareUpload = this.getWorkflow().createBashJob("prepare_upload");
//        prepareUpload.getCommand().addArgument("( cd $PCAWG_DIR && scripts/pcawg_wf_gen.py upload-prep --rsync boconnor@192.170.233.206:~boconnor/incoming/bulk_upload/ --rsync-key rsync_key.pem "+workflowID+" ) ");
//        prepareUpload.addParent(runBroadJob);
//
//        // TODO: need parameters for this
//        // TODO: need to check error state for each prep
//        Job runPrepUpload = this.getWorkflow().createBashJob("prepare_upload");
//        runPrepUpload.getCommand().addArgument("( cd $PCAWG_DIR && for i in upload/*/"+workflowID+"/*/prep.sh; do bash $i; done; ) ");
//        runPrepUpload.addParent(runBroadJob);
//
//        // TODO: need parameters for this
//        // TODO: need to check error state for each upload
//        Job doUpload = this.getWorkflow().createBashJob("do_upload");
//        doUpload.getCommand().addArgument("( cd $PCAWG_DIR && for i in upload/*/"+workflowID+"/*/upload.sh; do bash $i; done; ) ");
//        doUpload.addParent(runPrepUpload);
        
        return runBroadJob;
    }
    
    private Job checkForWorkflowFile(String workflowID, Job previousJob)
    {
        Job checkForWorkflowFileJob = this.getWorkflow().createBashJob("check_that_workflow_file_exists");
        // stat will terminate with exit code "1" if the file is not found.
        if (this.checkWorkflowFileExists)
            checkForWorkflowFileJob.getCommand().addArgument("stat "+this.workflowDir+"/"+workflowID);
        
        checkForWorkflowFileJob.addParent(previousJob);
        
        return checkForWorkflowFileJob;
    }
    
    private Job prepareUpload(String workflowID, String rsyncURL, String rsyncKey, Job previousJob)
    {
        Job prepareUploadJob = this.getWorkflow().createBashJob("prepare_upload");
        prepareUploadJob.getCommand().addArgument("( cd $PCAWG_DIR && /workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py upload-prep --rsync "+rsyncURL+" --rsync-key "+rsyncKey+" "+workflowID+ " ) ");
        prepareUploadJob.addParent(previousJob);
        // TODO: Need to find a way to verify successful completion!
        return prepareUploadJob;
    }
    
    private Job doPrepShellScript(String workflowID, Job previousJob)
    {
        Job doPrepJob = this.getWorkflow().createBashJob("do_prep_sh");
        //Do ALL of the prep scripts
        doPrepJob.getCommand().addArgument("( cd $PCAWG_DIR/upload && for i in upload/*/"+workflowID+"/*/prep.sh; do bash $i; done; )" );
        doPrepJob.addParent(previousJob);
        return doPrepJob;
    }
    
    private Job doUpload(String workflowID, Job previousJob)
    {
        Job doUploadJob = this.getWorkflow().createBashJob("do_upload_sh");
        //Do all of the uploads.
        doUploadJob.getCommand().addArgument("( cd $PCAWG_DIR/upload && for i in upload/*/"+workflowID+"/*/upload.sh; do bash $i; done; )" );
        doUploadJob.addParent(previousJob);
        return doUploadJob;
    }
}
