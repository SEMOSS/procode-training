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
    onePlusTwo: number;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const useAppContext = () => {
    const context = useContext(AppContext);
    if (!context) {
        throw new Error(
            'useAppContext must be used within an AppContextProvider',
        );
    }

    return context;
};

export const AppContextProvider = ({ children }: PropsWithChildren) => {
    const { isReady, actions } = useInsight();

    /**
     * State
     */
    const [onePlusTwo, setOnePlusTwo] = useState<number>();

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
        if (isReady) {
            (async () => {
                const response = await runPixel<number>('1 + 2');
                setOnePlusTwo(response);
            })();
        }
    }, [isReady, runPixel, setOnePlusTwo]);

    return (
        <AppContext.Provider value={{ runPixel, onePlusTwo }}>
            {children}
        </AppContext.Provider>
    );
};
