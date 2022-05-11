## Covid Node Trace

### I - Export Data From Firebase

Generate a private key file for your service account. In the Firebase console, open Settings > Service Accounts <br/>
Click Generate New Private Key, then confirm by clicking Generate Key <br/>
Securely store the JSON file containing the key. You may also check this [documentation](https://firebase.google.com/docs/admin/setup#initialize-sdk) <br/>
Rename the JSON file to credentials.json <br/>
Install [Node.js](https://nodejs.org/en/) <br/>
Open Node.js command prompt <br/>
- Install npm : `npm install -g firebase-tools` <br/>
- Login in : `firebase login` <br/>
- Retrieve data in JSON file : `npx -p node-firestore-import-export firestore-export -a credentials.json -b backup.json` <br/>

link use : [How to export data from Cloud Firestore to file?](https://stackoverflow.com/questions/70899359/how-to-export-data-from-cloud-firestore-to-file)
