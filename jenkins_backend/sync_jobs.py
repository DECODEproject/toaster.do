#!/usr/bin/env python3
"""
Module for backend talk with Jenkins executed by the web/CGI
"""

from argparse import ArgumentParser
from subprocess import run, PIPE
from os.path import join
import html

from config import (jarargs, jobpath, pypath)


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
    command = html.escape(command)

    replacements = [('DESC', desc),
                    ('SDK', sdk),
                    ('ARCH', arch),
                    ('COMMAND', command)]

    sdk_job = open(join(pypath, 'toasterbuild.xml'), encoding='utf-8').read()

    for i in replacements:
        sdk_job = sdk_job.replace('{{{%s}}}' % i[0], i[1])

    addargs = jarargs.copy()
    addargs.append('create-job')
    addargs.append(jobname.replace('@', 'AT'))

    run(addargs, input=sdk_job.encode())

    viewargs = jarargs.copy()
    viewargs.append('add-job-to-view')
    viewargs.append('web-sdk-builds')
    viewargs.append(jobname.replace('@', 'AT'))

    return run(viewargs)


def del_job(jobname):
    """
    Function for deleting a Jenkins job.
    """
    jarargs.append('delete-job')
    jarargs.append(jobname.replace('@', 'AT'))

    return run(jarargs)


def run_job(jobname):
    """
    Function for running a Jenkins job.
    """
    jarargs.append('build')
    jarargs.append(jobname.replace('@', 'AT'))

    return run(jarargs)


def list_jobs(account):
    """
    Function for listing Jenkins jobs.
    """
    jarargs.append('list-jobs')
    jarargs.append('web-sdk-builds')

    if account == 'all':
        return run(jarargs)

    joblist = run(jarargs, stdout=PIPE)
    joblist = joblist.stdout.decode()
    parsedlist = []
    for i in joblist.split():
        if i.startswith(account.replace('@', 'AT')):
                parsedlist.append(i)

    print('\n'.join(parsedlist))


def main():
    """
    Main routine.
    """
    parser = ArgumentParser()
    parser.add_argument('-n', '--dryrun', action='store_true')
    parser.add_argument('-a', '--add', action='store_true')
    parser.add_argument('-d', '--delete', action='store_true')
    parser.add_argument('-r', '--run', action='store_true')
    parser.add_argument('-l', '--list', action='store_true')
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
    elif args.run:
        if args.dryrun:
            print('Would build:', args.jobname)
            return
        print('Building job:', args.jobname)
        run_job(args.jobname)
    elif args.list:
        list_jobs(args.jobname)


if __name__ == '__main__':
    main()
