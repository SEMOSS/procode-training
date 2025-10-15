import { Stack, Typography } from "@mui/material";
import { useLoadingPixel } from "@/hooks";

/**
 * Renders the home page.
 *
 * @component
 */
export const HomePage = () => {
	/**
	 * Library hooks
	 */
	const [helloUserResponse, isLoadingHelloUser] =
		useLoadingPixel<string>("HelloUser()");
	const [callPythonResponse, isLoadingCallPython] =
		useLoadingPixel<string>("CallPython()");

	return (
		<Stack spacing={2}>
			<Typography variant="h4">Home page</Typography>
			<Typography>
				Welcome to the SEMOSS Template application! This repository is
				meant to be a starting point for your own SEMOSS application.
			</Typography>
			<Typography variant="h6">Example pixel call:</Typography>
			<ul>
				<li>
					<Typography variant="body1" fontWeight="bold">
						HelloUser()
					</Typography>
					<ul>
						<li>
							<Typography fontStyle="italic">
								{isLoadingHelloUser
									? "Loading..."
									: helloUserResponse}
							</Typography>
						</li>
					</ul>
				</li>
				<li>
					<Typography variant="body1" fontWeight="bold">
						CallPython()
					</Typography>
					<ul>
						<li>
							<Typography fontStyle="italic">
								{isLoadingCallPython
									? "Loading..."
									: callPythonResponse}
							</Typography>
						</li>
					</ul>
				</li>
			</ul>
		</Stack>
	);
};
