import { useLoadingPixel } from '@/hooks';
import { Stack, Typography } from '@mui/material';
import { Dropzone } from './library';

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

    return !engineId || isLoadingFiles ? (
        'Loading...'
    ) : (
        <Stack>
            <Typography>Files in Vector Database:</Typography>
            <Dropzone
                handleNewFiles={(files) =>
                    console.log('New files added', files)
                }
            />
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
