import { Engine } from '@/components';
import { useLoadingState } from '@/hooks';
import { useInsight } from '@semoss/sdk-react';
import {
    createContext,
    PropsWithChildren,
    useCallback,
    useContext,
    useEffect,
    useState,
} from 'react';

export interface AppContextType {
    runPixel: <T = unknown>(pixelString: string) => Promise<T>;
    isAppDataLoading: boolean;
    models: Engine[];
    databases: Engine[];
}

const AppContext = createContext<AppContextType | undefined>(undefined);

/**
 * Custom hook to get the stored app data and runPixel.
 *
 * @returns {AppContextType} - The data
 */
export const useAppContext = (): AppContextType => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error(
            'useAppContext must be used within an AppContextProvider',
        );
    }

    return context;
};

/**
 * Stores data accessible to the entire app. Must be used within an InsightProvider.
 *
 * @param {ReactNode} props.children The children who will have access to the app data.
 * @component
 */
export const AppContextProvider = ({ children }: PropsWithChildren) => {
    // Get the current state of the current insight
    const { actions, isReady } = useInsight();

    /**
     * State
     */
    const [isAppDataLoading, setIsAppDataLoading] = useLoadingState(true);
    const [models, setModels] = useState<Engine[]>();
    const [databases, setDatabases] = useState<Engine[]>();

    /**
     * Functions
     */
    const runPixel = useCallback(
        async <T,>(pixelString: string) => {
            const response = actions.run<T[]>(pixelString);
            return (await response).pixelReturn[0].output;
        },
        [actions],
    );

    /**
     * Effects
     */
    useEffect(() => {
        const loadAppData = async () => {
            const loadingKey = setIsAppDataLoading(true);

            interface LoadSetPair<T> {
                loader: () => Promise<T>;
                value?: T;
                setter: (value: T) => void;
            }

            const loadSetPairs: LoadSetPair<unknown>[] = [
                {
                    loader: async () =>
                        await runPixel<Engine[]>(
                            ` MyEngines ( metaFilters = [{ "tag" : "text-generation" }] , engineTypes = [ 'MODEL' ] )`,
                        ),
                    setter: (response) => setModels(response),
                } satisfies LoadSetPair<Engine[]>,
                {
                    loader: async () =>
                        await runPixel<Engine[]>(
                            ` MyEngines ( engineTypes = [ 'DATABASE' ] )`,
                        ),
                    setter: (response) => setDatabases(response),
                } satisfies LoadSetPair<Engine[]>,
            ];

            await Promise.all(
                loadSetPairs.map(
                    async (loadSetPair) =>
                        (loadSetPair.value = await loadSetPair.loader()),
                ),
            );

            setIsAppDataLoading(false, loadingKey, () =>
                loadSetPairs.forEach((loadSetPair) =>
                    loadSetPair.setter(loadSetPair.value),
                ),
            );
        };

        if (isReady) {
            // If the insight is ready, then load the app data
            loadAppData();
        }
    }, [isReady, runPixel]);

    return (
        <AppContext.Provider
            value={{ runPixel, models, isAppDataLoading, databases }}
        >
            {children}
        </AppContext.Provider>
    );
};
