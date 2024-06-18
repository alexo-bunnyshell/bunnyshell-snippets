## Context

problema cu imagePullSecrets e doar pt genericComponents like (Helm, Kube, TF, Generic); 
pt dockerCompose like, punem imagePullSecrets pe default ServiceAccount si Pods le rulam cu acest serviceAccount deci nu e o problema


ideea cu introdus logica de wait in flow-ul de deploy e pacatoasa, pt ca nu te ajuta deloc in toate celelalte cazuri, cum ar fi start si random restarts prin cluster, pe care eu nu le-as neglija; deci e doar o pacaleala, mi-a mers deploy-ul, gata am scapat; mai ales daca vrem sa ne ducem si spre zona de prod, nu vreau sa le recomandam astfel de solutii clientilor

Use `wait` with `dependsOn` when want to wait for is a Job, something that should finish, complete, you can use a wait component and rely only on dependsOn.

Use the `InitContainer` version when what you want to wait for is something that should be up and running. This checks will always run *inside* the cluster and will always do the check, *not only during deploy*.
