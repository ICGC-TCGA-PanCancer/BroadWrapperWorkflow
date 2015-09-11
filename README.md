# BroadWrapperWorkflow
A SeqWare workflow that wraps the Broad workflow.

## How to use
Ensure that the machine you are running this one has the docker images necessary for running Broad saved as tar files in the directory `/workflows/gitroot/pcawg_tools`.

You will also need to generate the `pcawg_data.service` file (using the script `$PCAWG_DIR/scripts/pcawg_wf_gen.py`) on this path:

`/workflows/gitroot/pcawg_tools/pcawg_data.service`

Then, you can run this workflow like this:


```
docker run --rm -h master -v /var/run/docker.sock:/var/run/docker.sock \
                          -v /datastore:/datastore \
                          -v /workflows:/workflows \
                          -v /workflows/gitroot/pcawg_tools:/workflows/gitroot/pcawg_tools/images:ro \
           pancancer/seqware_whitestar_pancancer:1.1.1 \
                seqware bundle launch --dir /workflows/Workflow_Bundle_BroadWrapper_0.0.1-SNAPSHOT_SeqWare_1.1.1  --engine whitestar --no-metadata
```
