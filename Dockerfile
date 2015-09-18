FROM pancancer/seqware_whitestar_pancancer:1.1.1

######################################
# This part is the PCAWG-tools section
######################################
USER root
RUN apt-get update
RUN apt-get install -y python wget build-essential python-dev git curl libffi-dev libssl-dev software-properties-common python-software-properties maven

# Install some python packages via pip
RUN wget https://bootstrap.pypa.io/get-pip.py && \
    python get-pip.py
RUN pip install requests[security]
RUN pip install synapseclient pandas

# create an ubuntu user
#RUN adduser --gid 1000 --uid 1000 --home /home/ubuntu ubuntu

# now everything else is Ubuntu which was renamed to seqware (why!?)
USER seqware

# TODO, I'm going to mount this in since the Azure instance has a 1TB volume here and the sofware stored here has been tweaked
# Setup working directories
#RUN mkdir /workflows && mkdir /workflows/gitroot
#WORKDIR /workflows/gitroot

# TODO, using a mounted version of this code for now
# Get repos for pcawg_tools and nebula
#RUN git clone https://github.com/ucscCancer/pcawg_tools.git && \
#    cd pcawg_tools && \
#    git checkout 1.0.0
#WORKDIR /workflows/gitroot
#RUN git clone https://github.com/kellrott/nebula.git
# FIXME: there are changes that need to be incorporated

# Set up environment variables
# FIXME: this just sets the env during the run of the Docker build!  THese need to be in the ~ubuntu/.bash_rc file!!
#ENV PCAWG_DIR /workflows/gitroot/pcawg_tools
#ENV NEBULA /workflows/gitroot/nebula
#ENV PYTHONPATH $PYTHONPATH:$NEBULA
# so this is how to do this really for within the container
CMD echo "export PCAWG_DIR=/workflows/gitroot/pcawg_tools">> ~/.bashrc
CMD echo "export NEBULA=/workflows/gitroot/nebula">> ~/.bashrc
CMD echo "export PYTHONPATH=\$NEBULA">> ~/.bashrc
CMD echo "cd; . venv/bin/activate">>~/.bashrc


#RUN chmod a+w /workflows && \
#    chmod a+w /workflows/gitroot/pcawg_tools && \
#    chmod a+w /workflows/gitroot/nebula

# Install docker into this container so that it can call other containers.
#RUN curl -sSL https://get.docker.com/ | sh

#RUN mv /workflows/gitroot/pcawg_tools/images /workflows/gitroot/pcawg_tools/old_images
#WORKDIR /workflows/gitroot/pcawg_tools
# The pcawg tools actually expect nebual to be in $PCAWG_DIR, although some of the docs suggest installing nebula into /workflows/gitroot, so just symlink it here.
#RUN ln -s ../nebula nebula
#RUN ln -s ../workflows workflows

#####################
# Set up the workflow
#####################

RUN mkdir -p /home/seqware/gitroot/BroadWrapperWorkflow
COPY src /home/seqware/gitroot/BroadWrapperWorkflow/src
COPY workflow /home/seqware/gitroot/BroadWrapperWorkflow/workflow
COPY pom.xml /home/seqware/gitroot/BroadWrapperWorkflow/pom.xml
COPY workflow.properties /home/seqware/gitroot/BroadWrapperWorkflow/workflow.properties
COPY links /home/seqware/gitroot/BroadWrapperWorkflow/links
WORKDIR /home/seqware/gitroot/BroadWrapperWorkflow/
RUN mvn clean package
#RUN cp -R target/Workflow_Bundle_BroadWrapper* /workflows/BroadWrapperWorkflow
#RUN rm -rf target/*
WORKDIR /home/seqware/gitroot/
