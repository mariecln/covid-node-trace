## Covid Node Trace

### I - Retrieve Data From Firebase

Generate a private key file for your service account. In the Firebase console, open Settings > Service Accounts
Click Generate New Private Key, then confirm by clicking Generate Key
Securely store the JSON file containing the key. You may also check this [documentation](https://firebase.google.com/docs/admin/setup#initialize-sdk)
Rename the JSON file to credentials.json
Install [Node.je](https://nodejs.org/en/)
Open Node.js command prompt
Install npm : `npm install -g firebase-tools`
Login in : `firebase login`
Retrieve data in JSON file : `npx -p node-firestore-import-export firestore-export -a credentials.json -b backup.json`
