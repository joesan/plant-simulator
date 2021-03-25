## GitHub Action Workflow

We use a simple tag based deployment. It works like this:

1. User pushes a commit into the plant-simulator repository

2. GitHub Action workflow kicks in @see main.yml

3. Depending on what type of commit is done, we either just build or build, push docker and write tag version in the
   plant-simulator-deployment project
   
4. To be able to push / write the tag version in the plant-simulator-deployment project, we need to create a few tokens:

   4.1. Create a new Personal access tokens (Go to Github User -> Settings -> Developer Settings -> Personal access token)
   4.2. Copy the newly generated Personal access token
   4.3. Go to the project (in this case, the plant-simulator project), go to Settings and then to Secrets
   4.4. Create a new secret and use the copied Personal access token as the secrets value