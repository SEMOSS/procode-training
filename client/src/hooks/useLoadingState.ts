import { useCallback, useRef, useState } from 'react';

export const useLoadingState = (initial: boolean = false) => {
    const [isLoading, setIsLoading] = useState<boolean>(initial);
    const loadingStateRef = useRef<number>(0);

    const toggleLoading = useCallback(
        (
            loading: boolean = true,
            key?: number,
            ifCurrent: () => void = () => null,
        ) => {
            if (loading) {
                setIsLoading(true);
                return ++loadingStateRef.current;
            } else if (loadingStateRef.current === key) {
                ifCurrent();
                setIsLoading(false);
            }
        },
        [],
    ) as ((loading?: true) => number) &
        ((loading: false, loadingKey: number, ifCurrent?: () => void) => void);

    const clearLoading = useCallback(() => {
        setIsLoading(false);
    }, []);

    return [isLoading, toggleLoading, clearLoading] as const;
};
