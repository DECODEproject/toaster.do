#!/usr/bin/env python3

from jenkins_creds import (jenkins_host, jenkins_cred)

# Path to jenkins-cli.jar
jarpath = '/var/cache/jenkins/war/WEB-INF/jenkins-cli.jar'

# jar parameters
jarargs = ['java', '-jar', jarpath, '-s', jenkins_host, '-auth', jenkins_cred]

# Physical path to where jobs are held
jobpath = '/srv/toaster'
