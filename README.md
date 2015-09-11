# BroadWrapperWorkflow
A SeqWare workflow that wraps the Broad workflow.

## Configuration
This workflow takes a few configuration options in its INI file:

```
jobs_dir=/tmp/BroadJobs
workflow_id=12345
workflow_dir=/workflows/gitroot/pcawg_tools/pcawg_data.tasks
container_name=pancancer/pcawg_tools:0.0.1
```

 - `jobs_dir` - This is the directory where workflow files will be generated. If you are generating workflow files like this:
```
scripts/pcawg_wf_gen.py gen --ref-download --create-service --work-dir <path_to_some_place_big>
```
then your INI file should have:
```
jobs_dir= <path_to_some_place_big>
```

 - `workflow_id` - This is the ID of the workflow file you want to run in this particular instance of BroadWrapperWorkflow. This will be the name of a workflow file in `jobs_dir`.  
 - `workflow_dir` - This actually should have the same value as `jobs_dir`. I'll remove one or the other once I get the chance...
 - `container_name` - This is the name of the container to run. This should be set to `pancancer/pcawg_tools:0.0.1`.

## How to use
Ensure that the machine you are running this one has the docker images necessary for running Broad saved as tar files (docker\_broad\_variant\_pipeline, docker\_gatk, docker\_genetorrent, docker\_muse, galaxy) in the directory `/workflows/gitroot/pcawg_tools`.

You will also need to generate the `pcawg_data.service` file (using the script `$PCAWG_DIR/scripts/pcawg_wf_gen.py`) on this path:

`/workflows/gitroot/pcawg_tools/pcawg_data.service`

The pcawg\_wf\_gen.py can also be used to generate workflow files. You will need to generate some before running BroadWrapperWorkflow, as these workflow files will be passed by BroadWrapperWorkflow to the underlying Broad pipeline as input data.

Once everything is set up, you can run this workflow like this:


```
docker run --rm -h master -v /var/run/docker.sock:/var/run/docker.sock \
                          -v /datastore:/datastore \
                          -v /workflows:/workflows \
                          -v /workflows/gitroot/pcawg_tools:/workflows/gitroot/pcawg_tools/images:ro \
           pancancer/seqware_whitestar_pancancer:1.1.1 \
                seqware bundle launch --dir /workflows/Workflow_Bundle_BroadWrapper_0.0.1-SNAPSHOT_SeqWare_1.1.1  --engine whitestar --no-metadata
```
