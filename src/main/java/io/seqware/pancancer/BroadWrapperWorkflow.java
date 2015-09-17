package io.seqware.pancancer;

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

    //private String jobsDir;
    private String workflowID;
    private String workflowDir;
    private boolean cleanup;

    private String pcawgContainerName ;

    private void init() {
        try {
            
//            if (hasPropertyAndNotNull("jobs_dir")){
//                this.jobsDir = getProperty("jobs_dir");
//            }
//            else
//            {
//                throw new RuntimeException("\"jobs_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
//            }

            if (hasPropertyAndNotNull("workflow_dir")){
                this.workflowDir = getProperty("workflow_dir");
            }
            else
            {
                throw new RuntimeException("\"workflow_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }
            
            if (hasPropertyAndNotNull("workflow_id")){
                this.workflowID = getProperty("workflow_id");
            }
            else
            {
                throw new RuntimeException("\"workflow_id\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }

            if (hasPropertyAndNotNull("container_name")){
                this.pcawgContainerName = getProperty("container_name");
            }
            else
            {
                throw new RuntimeException("\"container_name\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
            }            
//            if (hasPropertyAndNotNull("pcawg_dir")){
//                this.pcawgDir = getProperty("pcawg_dir");
//            }
//            else
//            {
//                throw new RuntimeException("\"pcawg_dir\" was not specified, or it had a null-value; it is NOT an optional parameter, and it is NOT nullable.");
//            }

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

        // TODO: Add a bash job to check that the workflow file actually exists where it is supposed to and returns an error code if it does not.
        // The Broad scripts may fail if the file does not exist, but they may
        // not return a non-zero error code, so this workflow will finish very quickly and *appear* successful when it is not.
        
        // Run the workflow.
        Job runBroad = this.runBroadWorkflow(this.workflowID,generateJobs);
        //cleanupWorkflow(runBroad);
        
        // TODO: Maybe  have a post-Broad step that outputs the <WORKFLOWID>.err and <WORKFLOWID>.out files so that they are a part of the seqware output?
        // It seems like a lot of Broad scripts will exit with code 0 even if they encountered an error (missing input file, HTTP timeout, etc...) and ended
        // so SeqWare might "succeed" even when Broad fails.
        
        // TODO: Upload steps! Not yet certain if there will be one upload script wrapping the other steps, or if this workflow will call them all separately.
    }
    
//    private void cleanupWorkflow(Job... lastJobs) {
//        Job cleanupJob = null;
//        if (cleanup) {
//            cleanupJob = this.getWorkflow().createBashJob("cleanup");
//            // Clean up the jobsDir 
//            cleanupJob.getCommand().addArgument("rm -Rf "+this.jobsDir+" \n");
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
        generateWFFilesJob.getCommand().addArgument("/workflows/gitroot/pcawg_tools/scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir "+this.workflowDir);
        
        return generateWFFilesJob;
    }
    
    private Job runBroadWorkflow(String workflowID, Job previousJob)
    {
        //Need to execute: qsub sge_qsub_runworkflow.sh pcawg_data.service pcawg_data.tasks/<workflow_id_fill_in>
        Job runBroadJob = this.getWorkflow().createBashJob("run_broad_workflow");
        
        // This shouldn't need "sudo" but it won't run without it. :/ 
        runBroadJob.getCommand().addArgument("sudo docker run --rm -h master"
                                                            // Mount the docker socket so that the container can call docker at the top-level
                                                            +" -v /var/run/docker.sock:/var/run/docker.sock "
                                                            // Mount the images directory. This directory contains the TAR files for all of the images necessary for running Broad 
                                                            +" -v /workflows/gitroot/pcawg_tools/images:/workflows/gitroot/pcawg_tools/images:ro "
                                                            // Mount the service config file - needs to be created when generating workflow files, before this step runs!
                                                            +" -v /workflows/gitroot/pcawg_tools/pcawg_data.service:/workflows/gitroot/pcawg_tools/pcawg_data.service:ro"
                                                            // Mount the datastore, datastore will need to contain "nebula/work"; not certain if nebula needs this already to exist, or if it just needs /datastore and will set up its own directories. 
                                                            +" -v /datastore:/datastore "
                                                            // Mount the directory with the work that needs to be done.
                                                            +" -v " + this.workflowDir+":/tasks "
                                                            + this.pcawgContainerName+" /workflows/gitroot/pcawg_tools/sge_qsub_runworkflow.sh  pcawg_data.service /tasks/"+workflowID);
        runBroadJob.addParent(previousJob);
        
        return runBroadJob;
    }
}
