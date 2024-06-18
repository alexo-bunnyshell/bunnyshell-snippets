
## Context

Postgres (or other service) will start up (and pass a health check) and then reboot itself on initialization.  So I need to make sure postgres has completed its reboot before starting the rest of my services

## Explanation

Things are a bit more complex under the hood:

- with Bunnyshell, your environments run on Kubernetes
- `dependsOn` (the one we know from docker compose) doesn’t exist as concept in kubernetes, pods in kubernetes are a lot more volatile, they can be restarted or rescheduled on other nodes. Kubernetes leaves it to the user to check that dependent services are available before the main container starts. So it is up to the user to design the application to be resilient to these kind of interruptions.
- Bunnyshell `dependsOn` doesn’t try to “add” this missing feature to kubernetes, because it would not be possible. Bunnyshell `dependsOn` makes it possible to manage the order in which components are deployed. Think of it as successive “kubectl apply” commands, if it succeed, will continue with the subsequent component, or if it fails it will stop the deploy process. Bunnyshell `dependsOn` is not used on start, stop nor destroy order.
- In Kubernetes, Readiness probes are used by the Kubelet know when a container is ready to start accepting traffic, so they are useful, but do not solve the problem of waiting for dependant services.
- The usual way to achieve the desired functionality (waiting for databases or other serviced to be available) in Kubernetes, is to use InitContainers for components that depend on those services.

```yaml
initContainers:
  - name: init-myservice
    image: busybox
    command: ['sh', '-c', 'until nc -z mydb 5432; do echo waiting for mydb; sleep 2; done;']
```

or, where possible, to update the entrypoint of the image, to make the checks before actually starting the main process.

```bash
timeout 5m sh -c 'until mysqladmin ping --host="$DATABASE_HOST" --port="$DATABASE_PORT" --user="$DATABASE_USER" --password="$DATABASE_PASSWORD" | grep "alive" > /dev/null 2>&1; do echo "Waiting for database connection..." && sleep 5; done'
if [ $? -ne 0 ]; then
  echo "timed out while waiting for database to be ready"
  exit 1
fi
```

Here is an example suited for your services:

```yaml
-
        kind: Database
        name: postgresql
        dockerCompose:
            image: 'rxvantage/postgresql:15-2024-01-30'
            ports:
                - '5432:5432'
            labels:
                kompose.service.healthcheck.readiness.test: 'CMD pg_isready'
                kompose.service.healthcheck.readiness.interval: 10s
                kompose.service.healthcheck.readiness.timeout: 10s
                kompose.service.healthcheck.readiness.retries: 5
                kompose.service.healthcheck.readiness.start_period: 90s
    -
        kind: Service
        name: epsio
        dockerCompose:
            image: 'us-docker.pkg.dev/epsio-io/public/epsio:19'
            environment:
                EPSIO_SHOULD_RESTORE_FROM_BACKUP_ON_STARTUP: '1'
                EPSIO_SOURCE_DATABASE_TYPE: postgres
        pod:
            init_containers:
            - from: wait-for-postgres
              name: wait-for-postgres
        dependsOn:
            - postgresql

    -   kind: InitContainer
        name: wait-for-postgres
        dockerCompose:
            image: busybox
            entrypoint: /bin/sh
            command:
               - '-c'
               - 'until nc -z -v -w30 postgresql 5432; do echo "waiting for postgres..."; sleep 1; done; echo "postgres is up"'
```