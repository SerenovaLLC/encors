#!groovy​
@Library('sprockets@2.1.0') _

import common
import git
import hipchat
import lein

def service = 'encors'
def c = new common()
def g = new git()
def h = new hipchat()
def l = new lein()

node() {
pwd = pwd()
echo pwd
 }

if (pwd ==~ /.*PR.*/ ) {
  node() {
    try {
      timeout(time: 1, unit: 'HOURS') {
        ansiColor('xterm') {
          stage ('SCM Checkout') {
            g.checkOut()
          }
          stage ('Export Properties') {
            l.export()
            def props = readProperties([file: 'export.properties'])
            pr_version =  "${props.POM_VERSION}"
            c.setDisplayName("${pr_version}")
          }
          stage ('Test') {
            l.check()
            l.test2junit()
            c.publishTest()
          }
          stage ('Notify Success') {
            h.hipchatPullRequestSuccess("${service}", "${pr_version}")
          }
        }
      }
    }
    catch (err) {
      h.hipchatPullRequestFailure("${service}", "${pr_version}")
      echo "Failed: ${err}"
      error "Failed: ${err}"
    }
    finally {
      c.cleanup()
    }
  }
}
else if (pwd ==~ /.*master.*/ ) {
  node() {
    try {
      timeout(time: 1, unit: 'HOURS') {
        ansiColor('xterm') {
          stage ('SCM Checkout') {
            g.checkOutFrom("${service}.git")
          }
          stage ('Export Properties') {
            l.export()
            def props = readProperties([file: 'export.properties'])
            build_version =  "${props.POM_VERSION}"
            c.setDisplayName("${build_version}")
          }
          stage ('Publish') {
            l.check()
            l.deploy()
            g.publishGit("${build_version}")
          }
          stage ('Notify Success') {
            h.hipchatBuildSuccess("${service}", "${build_version}")
          }
        }
      }
    }
    catch (err) {
      h.hipchatBuildFailure("${service}", "${build_version}")
      echo "Failed: ${err}"
      error "Failed: ${err}"
    }
    finally {
      c.cleanup()
    }
  }
}
else {
  stage ('Error')
  error 'No Stage'
}