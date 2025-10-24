import { Delete } from "@mui/icons-material";
import { IconButton, Stack, Typography } from "@mui/material";
import type { SemossFile } from "@/types";

export interface FileRowProps {
	file: SemossFile;
	onDelete?: () => void;
}

export const FileRow = ({ file, onDelete }: FileRowProps) => {
	return (
		<Stack
			direction="row"
			key={file.fileName}
			spacing={2}
			justifyContent="space-between"
		>
			<Typography>{file.fileName}</Typography>
			<Stack direction="row" spacing={2} alignItems="center">
				<Typography>{file.fileSize.toFixed(2)} KB</Typography>
				<IconButton onClick={onDelete}>
					<Delete color="error" />
				</IconButton>
			</Stack>
		</Stack>
	);
};
