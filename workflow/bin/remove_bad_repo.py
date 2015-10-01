#! /usr/bin/python3

import json
import sys
import re

bad_repo = sys.argv[1]
path_to_workflow_file = sys.argv[2]
print('repo is: '+bad_repo)
print('workflow file is: '+path_to_workflow_file)
json_data = {}
with open(path_to_workflow_file) as json_file:
    json_data = json.load(json_file)
    print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
    print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])

    # now remove the bad repo from tumour and normal
    # the regular expression is to clean up extra commas at the begining or end of string, or any multiple-commas created by removing the bad repo name
    bad_repos = bad_repo.split(',')
    for repo in bad_repos:
        json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"] = re.sub(r'^,|,{2,}|,$','', json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"].replace(repo,'')  )
        json_data["parameters"]["normal_bam_download"]["gnos_endpoint"]=  re.sub(r'^,|,{2,}|,$','', json_data["parameters"]["normal_bam_download"]["gnos_endpoint"].replace(repo,'') )
        print('\nafter cleaning out \''+repo+'\': ')
        print('tumour endpoint: '+json_data["parameters"]["tumor_bam_download"]["gnos_endpoint"])
        print('normal endpoint: '+json_data["parameters"]["normal_bam_download"]["gnos_endpoint"])

with open (path_to_workflow_file,'w') as json_file:
    json_file.write(json.dumps(json_data))