# SEMOSS Template App

## Overview

This repository provides a starting point for SEMOSS React applications. It includes:

- A structure for connecting a React front-end with a Java back-end.
- Example custom Java reactors and TypeScript components that interact with them.
- Development tools: Prettier, ESLint, pre-commit, and lint-staged.

---

## Prerequisites

Before using this repository, ensure you have the following:
- **Node.js**
  - Install Node.js from the Node.js website.
  - Version v20.19.0 is recommended for this training.
- **Code Editor**
  - Use a code editor of your choice.
  - Visual Studio Code is suggested for this training.
- **pnpm Package Manager**
  - This project uses pnpm for managing frontend dependencies.
  - Install pnpm globally by running: `npm install -g pnpm`
- **Basic Git knowledge:**  
  [Using SSH keys with GitHub](https://docs.github.com/en/authentication/connecting-to-github-with-ssh) is recommended for authentication.

---

## Creating a React App Using This Repository

### Clone the Template
1. Create and Navigate to a Local Project Folder
2. Clone the Repository Locally
   - In your project terminal, run: `git clone https://github.com/SEMOSS/procode-training.git` or `git@github.com:SEMOSS/procode-training.git`, if using SSH keys

### Running the Application
1. Open the project in your code editor (VS Code recommended)
   - Launch Visual Studio Code.
   - Open the procode-training folder.
   - Open a new terminal in VS Code.
2. Navigate to the Client Folder
   - In the terminal, move into the client directory: `cd client`
3. Install Frontend Dependencies
   - Still in the client directory, run: `pnpm install`

### Connecting to AI Core
1. Create a .env.local file in the client folder.
2. If you do not already have a access keys, generate them within the platform following these [instructions](https://workshop.cfg.deloitte.com/docs/platform-navigation/Settings/MyProfile#access-tokens).
3. Add your client-specific keys to the .env.local file as follows:
   - CLIENT_ACCESS_KEY="YOUR_CLIENT_ACCESS_KEY"
   - CLIENT_SECRET_KEY="YOUR_CLIENT_SECRET_KEY"

### Starting the Local Frontend Server
- Ensure Terminal Is in the Client Folder
- Run the following to launch the local dev server: `pnpm dev`
- Connect to AI Core and Verify Application
  - Wait for the server to fully start.
  - The local frontend (http://localhost:5173) should automatically connect to AI Core.
  - Once loaded, the application interface will be available for use.

---

## SEMOSS App Structure

- The "Publish files" button in the SEMOSS UI creates a snapshot for users.
- The `portals` folder contains files available to users, including `portals/index.html` (the app's main entry point).
- Front-end source code typically lives in the `client` folder and is bundled into `portals` using Webpack (see `client/README.md` for details).
- Back-end Java reactors are in the `java` folder. When you click "Recompile reactors" in the SEMOSS UI, SEMOSS compiles these and places `.class` files in the `classes` folder (see `java/README.md` for more).

---

## Plug-ins

This repository includes several tools to help maintain code quality:

- [Prettier](https://prettier.io/docs/): Formats your front-end code for consistency.
- [ESLint](https://eslint.org/docs/latest/use/core-concepts/): Checks your front-end code for quality and common errors.
- [pre-commit](https://pre-commit.com/): Ensures code formatting and quality checks are run before committing (primarily for back-end code).
- [lint-staged](https://github.com/okonet/lint-staged): Runs Prettier and ESLint on staged files before each commit to prevent bad code from being pushed.
  - Note: if you are a Mac user, and your commits are erroring out with the message `" not foundECURSIVE_EXEC_FIRST_FAIL  Command "lint-staged`, then you likely need to change your line-endings in `./husky/pre-commit` and `./husky/commit-msg` from CRLF to LF.
- [commitizen](https://www.conventionalcommits.org/en/v1.0.0/): This pre-commit hook uses conventional commit syntax. Check this link out to understand how to format your commit messages. Here are some common examples:
  - `feat: Major feature added`
  - `docs: Documentation changed/updated`
  - `fix: Bug fixed and code restored to working state`
  - `refactor(style): Changed some code around to adhere to better style`
  - `chore: Some update/chore that you've been needing to do`

---

## Next Steps

- See `client/README.md` for front-end development instructions.

---

## Support

For questions or issues, contact the SEMOSS team or refer to internal documentation.
