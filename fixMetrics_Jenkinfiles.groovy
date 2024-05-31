import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
def BASTION_DC = ''
def BASTION_DC2 = ''
def BASTION_DR = ''
def HYDRO_BUILD = ''
def USER = ''
def SITES = []
def IMAGES_LOCATION = ''
def KUBE_RBAC_PROXY_IMAGE = ''
def ARGS_IMAGES = []
def CLUSTER_MONITORING_OPERATOR = ''
node("built-in") {
    stage("Select a site"){
        env.SITE = input(
            id: 'siteInput',
            message: 'Select a site:',
            parameters: [
                choice(
                    name: 'SITE',
                    choices: SITES,
                    description: 'Choose a site from the list'
                )
            ]

        )     
        if (site == "DC") {
            env.BASTION = BASTION_DC
        } else if (site == "DC2") {
            env.BASTION = BASTION_DC2
        } else if (site == "DR") {
            env.BASTION = BASTION_DR
        }
    }
    stage("Get the image in need") {
        withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {        
            sh """
                rsync -avz -e "sshpass -p BASTION_PASS ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null" ${USER}@${HYDRO_BUILD}:${IMAGES_LOCATION} .
                rsync -avz --delete ${IMAGES_LOCATION.tokenize('/').last()}/* -e "sshpass -p BASTION_PASS ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null" ${USER}@${BASTION}:${IMAGES_LOCATION}
            """        

        }
    }    
    stage("Push the image in need") {
        withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {        
            sh """
                sshpass -p BASTION_PASS ssh ${USER}@${BASTION} 'cd ${IMAGES_LOCATION} && \
                                                                    podman login -u kubeadmin -p \$(oc whoami -t) registry.apps.${SITE.toLowerCase()}-ducptm.com && \
                                                                    sh pushMonitoringImages.sh ${SITE.toLowerCase()}
                                                                    '
            """        

        }
    }

    stage("Get YAML file") {
        withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {    
           def yamlString = sh(
                script: """
                        sshpass -p BASTION_PASS ssh ${USER}@${BASTION_DC2} 'oc get deployment/cluster-monitoring-operator -n openshift-monitoring  -o yaml'
                        """, 
                returnStdout: true
                ).trim()
        // Parse YAML string
            def yaml = new Yaml().load(yamlString)

            // Modify YAML content
            yaml.spec.template.spec.containers.each { container ->
                if (container.name == 'cluster-monitoring-operator') {
                    container.args = container.args.findAll { !it.startsWith('-images=') }
                    container.args += ARGS_IMAGES
                    container.image = CLUSTER_MONITORING_OPERATOR
                }
                if (container.name == 'kube-rbac-proxy') {
                    container.image = KUBE_RBAC_PROXY_IMAGE
                }
        }
            yaml.spec.template.spec.securityContext.runAsGroup = 65534
            yaml.spec.template.spec.securityContext.runAsNonRoot = true
            yaml.spec.template.spec.securityContext.runAsUser = 65532

            // Convert modified YAML to string
            def modifiedYamlString = new Yaml().dumpAs(yaml, null, DumperOptions.FlowStyle.BLOCK)

            env.tempFile = env.WORKSPACE + "/cluster-monitoring-operator_deployment.yaml"

            writeFile file: tempFile, text: modifiedYamlString                      
    }
    }
    stage("Transfer new Config to Bastionhost") {   
        withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {        
            sh """
                rsync -avz ${tempFile} -e "sshpass -p BASTION_PASS ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null" ${USER}@${BASTION}:${IMAGES_LOCATION}
            """        
        }          
     
    }
    stage("Appply new Config") {     
        withCredentials([string(credentialsId: 'bastion', variable: 'BASTION_PASS')]) {        
            sh """
                sshpass -p BASTION_PASS ssh ${USER}@${BASTION_DC2} 'oc apply -f ${IMAGES_LOCATION}/cluster-monitoring-operator_deployment.yaml'
            """        

        }        
      
    }    


}















