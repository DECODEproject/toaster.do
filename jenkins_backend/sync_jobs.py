#!/usr/bin/env python3
"""
Module for backend talk with Jenkins executed by the web/CGI
"""

from argparse import ArgumentParser
from subprocess import run

from config import jarpath, jobpath
from jenkins_creds import (jenkins_host, jenkins_user, jenkins_pass)


def html_escape(string):
    """
    Function for escaping certain symbols to XML-compatible sequences.
    """
    html_codes = [("'", '&apos;'), ('"', '&quot;'), ('&', '&amp;'),
                  ('<', '&lt;'), ('>', '&gt;')]
    for i in html_codes:
        string = string.replace(i[0], i[1])

    return string


def add_job(jobname):
    """
    Function for adding a job to Jenkins.
    """
    info = jobname.split('-')
    desc = 'WebSDK build for: %s\nStarted: %s' % (info[0], info[2])
    sdk = info[1].split('_')[0]
    arch = info[1].split('_')[1]
    blendfile = '%s/%s/Dockerfile' % (jobpath, jobname)

    if sdk == 'arm':
        board = info[1].split('_')[2]
        zshcmd = 'load devuan %s %s' % (board, blendfile)
    elif sdk == 'live':
        zshcmd = 'load devuan %s %s' % (arch, blendfile)
    elif sdk == 'vm':
        zshcmd = 'load devuan %s' % (blendfile)

    command = "zsh -f -c 'source sdk && %s && build_image_dist'" % zshcmd
    command = html_escape(command)

    replacements = [('DESC', desc),
                    ('SDK', sdk),
                    ('ARCH', arch),
                    ('COMMAND', command)]

    sdk_job = open('toasterbuild.xml', encoding='utf-8').read()

    for i in replacements:
        sdk_job = sdk_job.replace('{{{%s}}}' % i[0], i[1])

    creds = '%s:%s' % (jenkins_user, jenkins_pass)
    clijob = run(['java', '-jar', jarpath, '-s', jenkins_host, '-auth', creds,
                  'create-job', jobname.replace('@', 'AT')],
                 input=sdk_job.encode())

    return clijob


def del_job(jobname):
    """
    Function for deleting a Jenkins job.
    """
    creds = '%s:%s' % (jenkins_user, jenkins_pass)
    clijob = run(['java', '-jar', jarpath, '-s', jenkins_host, '-auth', creds,
                  'delete-job', jobname.replace('@', 'AT')])

    return clijob


def main():
    """
    Main routine.
    """
    parser = ArgumentParser()
    parser.add_argument('-a', '--add', action='store_true')
    parser.add_argument('-d', '--delete', action='store_true')
    parser.add_argument('-n', '--dryrun', action='store_true')
    parser.add_argument('jobname')
    # NOTE: jobname should be email-arch-date, and a predefined directory
    # somewhere on the filesystem. e.g.:
    # - parazyd@dyne.org-vm_amd64-198374198
    # - parazyd@dyne.org-arm_armhf_raspi2-2198361991

    args = parser.parse_args()


    if args.add:
        if args.dryrun:
            print('Would add:', args.jobname)
            return
        print('Adding job:', args.jobname)
        add_job(args.jobname)
    elif args.delete:
        if args.dryrun:
            print('Would remove:', args.jobname)
            return
        print('Removing job:', args.jobname)
        del_job(args.jobname)


if __name__ == '__main__':
    main()
