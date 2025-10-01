import { CloudUpload } from '@mui/icons-material';
import { styled, Typography } from '@mui/material';
import { InputHTMLAttributes } from 'react';
import { DropzoneRootProps, useDropzone } from 'react-dropzone';

export interface DropzoneProps {
    handleNewFiles?: (acceptedFiles: File[]) => void;
    disabled?: boolean;
}

const DropzoneContainer = styled('div')<
    DropzoneRootProps & { isDragActive: boolean }
>(({ theme, isDragActive }) => ({
    border: `2px dashed ${theme.palette.grey[500]}`,
    borderRadius: theme.shape.borderRadius,
    padding: theme.spacing(5),
    textAlign: 'center',
    cursor: 'pointer',
    color: theme.palette.text.secondary, // Grey text
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    backgroundColor: isDragActive
        ? theme.palette.action.hover
        : theme.palette.common.white,
}));

/**
 * Renders a dropzone for file uploads
 *
 * @component
 */
export const Dropzone = ({
    disabled = false,
    handleNewFiles,
}: DropzoneProps) => {
    /**
     * Library hooks
     */
    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop: handleNewFiles,
        accept: {
            'application/pdf': ['.pdf'],
        },
        disabled,
        onDragEnter: () => null,
        onDragOver: () => null,
        onDragLeave: () => null,
        multiple: true,
    });

    return (
        <DropzoneContainer isDragActive={isDragActive} {...getRootProps()}>
            <input
                {...(getInputProps() as InputHTMLAttributes<HTMLInputElement>)}
            />

            <CloudUpload />
            {isDragActive ? (
                <Typography variant="body2">Drop the files here ...</Typography>
            ) : (
                <Typography variant="body2">
                    Drag and drop files here, or click to select files
                </Typography>
            )}
        </DropzoneContainer>
    );
};
