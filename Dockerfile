FROM pancancer/seqware_whitestar_pancancer:1.1.1

######################################
# This part is the PCAWG-tools section
######################################
USER root
RUN apt-get update

# The last two lines in this apt-get install is for gt-upload-download-wrapper and vcf-uploader.
RUN apt-get install -y	python	wget	curl	tabix	git	rsync	maven \
						python-virtualenv	python-dev	build-essential \
						libffi-dev	libssl-dev	software-properties-common \
						python-software-properties	openssh-client \
						libcurl3 libxqilla6 libboost-program-options1.54.0 libboost-system1.54.0  libboost-filesystem1.54.0 libboost-regex1.54.0 \
						python-pip libxml-dom-perl libxml-xpath-perl libjson-perl libxml-libxml-perl time libdata-uuid-libuuid-perl libcarp-always-perl libipc-system-simple-perl libdata-uuid-perl vim samtools

# Install some python packages via pip
RUN wget https://bootstrap.pypa.io/get-pip.py && \
    python get-pip.py
# Installing requets[security] might clean up some of the security warnings seen when installing other packages...
RUN pip install requests[security]
# The last line in this pip install is for gt-upload-download-wrapper and vcf-uploader.
RUN pip install synapseclient pandas \
	python-dateutil elasticsearch xmltodict pysftp paramiko

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
RUN echo "export PCAWG_DIR=/workflows/gitroot/pcawg_tools">> /home/seqware/.bashrc
RUN echo "export NEBULA=/workflows/gitroot/nebula">> /home/seqware/.bashrc
RUN echo "export PYTHONPATH=\$NEBULA">> /home/seqware/.bashrc
RUN echo "cd; . venv/bin/activate">>/home/seqware/.bashrc


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

############################################
# Some stuff for gt-download-upload-wrapper
# and vcf-uploader
############################################

# #RUN apt-get update && apt-get install -y wget
# RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-download_3.8.7-ubuntu2.207-14.04_amd64.deb
# RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-common_3.8.7-ubuntu2.207-14.04_amd64.deb
# RUN cd /opt && wget -t 5 --timeout=5 --no-check-certificate https://cghub.ucsc.edu/software/downloads/GeneTorrent/3.8.7/genetorrent-upload_3.8.7-ubuntu2.207-14.04_amd64.deb
# # Combining these installs with the main apt-get install at the begining of this dockerfile.
# #RUN apt-get update && apt-get install -y libcurl3 libxqilla6 libboost-program-options1.54.0 libboost-system1.54.0  libboost-filesystem1.54.0 libboost-regex1.54.0 
# # Install genetorrent
# RUN cd /opt && dpkg --install genetorrent-download_3.8.7-ubuntu2.207-14.04_amd64.deb genetorrent-common_3.8.7-ubuntu2.207-14.04_amd64.deb genetorrent-upload_3.8.7-ubuntu2.207-14.04_amd64.deb
# # Get gt-download-upload-wrapper and vcf-uploader
# RUN mkdir -p /opt/gt-download-upload-wrapper && cd /opt/gt-download-upload-wrapper && wget --no-check-certificate https://github.com/ICGC-TCGA-PanCancer/gt-download-upload-wrapper/archive/2.0.13.tar.gz && tar zxf 2.0.13.tar.gz
# RUN mkdir -p /opt/vcf-uploader && cd /opt/vcf-uploader && wget --no-check-certificate https://github.com/ICGC-TCGA-PanCancer/vcf-uploader/archive/2.0.7.tar.gz && tar zxf 2.0.7.tar.gz
# # Combining these installs with the main apt-get install at the begining of this dockerfile.
# # RUN apt-get update && apt-get install -y python-dev python-pip libxml-dom-perl libxml-xpath-perl libjson-perl libxml-libxml-perl time libdata-uuid-libuuid-perl libcarp-always-perl libipc-system-simple-perl libdata-uuid-perl curl vim samtools tabix

# # Combining these pip installs with the other pip-install earlier in this Dockerfile
# #RUN pip install synapseclient python-dateutil elasticsearch xmltodict pysftp paramiko
# # It's probably no longer necessary to manually install sudo - it seems to work fine automatically on more current versions of docker with newer base images.
# #RUN apt-get update && apt-get install -y sudo
# # This is probably not necessary since we're basing this container on pancancer/seqware_whitestar_pancancer:1.1.1
# #RUN useradd seqware

# # Test perl scripts
# RUN perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.13/lib /opt/vcf-uploader/vcf-uploader-2.0.7/gnos_upload_vcf.pl && \
#     perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.13/lib /opt/vcf-uploader/vcf-uploader-2.0.7/gnos_download_file.pl && \
#     perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.13/lib /opt/vcf-uploader/vcf-uploader-2.0.7/get_donors_by_elastic_search.pl && \
#     perl -c -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.13/lib /opt/vcf-uploader/vcf-uploader-2.0.7/synapse_upload_vcf.pl
