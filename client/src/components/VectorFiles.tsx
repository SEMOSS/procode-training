import { useLoadingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';

export interface VectorFilesProps {
    engineId?: string;
}

interface SemossFile {
    fileName: string;
    fileSize: number; // in KB
    lastModified: string; // "YYYY-MM-DD HH:mm:ss"
}

/**
 * Renders the vector files page, allowing users to view and add files
 *
 * @component
 */
export const VectorFiles = ({ engineId }: VectorFilesProps) => {
    /**
     * Library hooks
     */
    const [files, isLoadingFiles] = useLoadingPixel<SemossFile[]>(
        engineId
            ? `ListDocumentsInVectorDatabase(engine=${JSON.stringify(engineId)})`
            : undefined,
        [],
    );

    console.log(isLoadingFiles, files);
    return !engineId || isLoadingFiles ? (
        'Loading...'
    ) : (
        <Stack>
            <Typography>Files in Vector Database:</Typography>
            {files.map((file) => (
                <Stack
                    direction="row"
                    key={file.fileName}
                    spacing={2}
                    justifyContent="space-between"
                >
                    <Typography>{file.fileName}</Typography>
                    <Typography>{file.fileSize.toFixed(2)} KB</Typography>
                </Stack>
            ))}
        </Stack>
    );
};
