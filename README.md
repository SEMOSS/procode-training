## Purpose

This repository is a blank canvas to use for SEMOSS React apps. It includes the necessary structure to connect a React front-end with a Java back-end, and contains examples of custom java reactors and TypeScript components that call those reactors. This repository also includes several plug-ins that enhance the development workflow, such as Prettier, ESLint, pre-commit, and lint-staged.

## Install

A prerequisite for using this repository is having SEMOSS installed on your computer. As of 8/6/25, the installation guide can be found [at this link](https://amedeloitte.sharepoint.com/:p:/r/sites/SEMOSS/_layouts/15/Doc.aspx?sourcedoc=%7B4234D7E0-E161-4168-B889-29B4BBE07C67%7D&file=SEMOSS%20DEV%20Install_2024-07-24%20Working%20Version.pptx&action=edit&mobileredirect=true). To verify that SEMOSS is installed correctly, navigate to your local SEMOSS UI at [http://localhost:9090/SemossWeb/packages/client/dist/#/](http://localhost:9090/SemossWeb/packages/client/dist/#/) - the webpage should load.

### Pro code app creation

The first step of local development is creating an App on your local SEMOSS instance:
- Navigate to your local SEMOSS UI at [http://localhost:9090/SemossWeb/packages/client/dist/#/](http://localhost:9090/SemossWeb/packages/client/dist/#/). From now on, this page will be referred to as 'the SEMOSS UI'
- Navigate to the App page (typically listed as an option on the left side of the landing page)
- Click on "Create New App"
- Select the option to develop your app in code (as opposed to Drag & drop or others)
- Give your app a name and description - these selections are up to your preference; this example will proceed with the name `YourAppName`
- Click on the submit button to create your app, and you will be taken to the editor page
- Note that in the URL, you will see a long string of random digits - this is the ID of your app, which from now on will be referred to as `your-app-id`
    - As an example: `1337e31c-2131-4ef4-b942-94bdffa65c3f`

### Navigating your pro-code app

Now that you've created your app, let's discuss its file structure. On the app editor page, you should see a file explorer component that is displaying a folder called `portals`. Note that although you can see `portals`, the file explorer component itself is actually displaying the *contents* of the folder `assets` - as in, `portals` is inside of `assets`, and if `assets` had more files, you would be able to see them in the file explorer too. We will revisit the assets folder later, but for now just note that `assets` is the main folder for the app's code, and that `portals` lives inside of it.

Inside of `portals` is `index.html`. Clicking on `index.html` will show you its contents, which are a basic placeholder for an app. When rendering your App, SEMOSS looks for `portals/index.html`.

Let's now look at your laptop's file explorer to see where these files live.
- Open a new file explorer window at your `workspace` folder
- Navigate to `workspace/Semoss/project/[YourAppName]_[YourAppID]/app_root/version/`
- Notice that inside of `version`, you can now see the `assets` folder - as discussed, `assets` is the main folder for the app's code
- Clicking inside of `assets`, you can now seen the familiar `portals` folder and `portals/index.html` files

The `assets` folder you've found here, and the assets folder in the SEMOSS UI's file explorer are the exact same folder; you are just viewing them via two different methods.

Lastly for this section, let's discuss how to make changes to your APP that show up in the SEMOSS UI. Navigate back to your app in the SEMOSS UI, and open `portals/index.html`. Change some text inside of this file, then hit the "Save" button on the page. Click back into the "App" tab, and notice that the text remains unchanged, even if you refresh the page. The `workspace/Semoss/project/YourAppName_your-app-id/app_root/version/assets` is where you should *edit* your app, but while you're editing, it would be inconvenient if all of your app users could see changes as you make them. For that reason, the Public version of your app is actually a *snapshot* of your code, taken when you click on the "Publish files" button. Click that button, refresh your page, and verify that your edits to `portals/index.html` now do show up on the App tab.

Note: when you click on "Publish files", the code snapshot created lives in `workspace/apache-tomcat-9.0.102/webapps/Monolith/public_home/your-app-id`