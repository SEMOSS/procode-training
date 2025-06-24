import { useAppContext } from '@/contexts';

export const PageA = () => {
    const { onePlusTwo } = useAppContext();

    return <div>{`1 + 2 = ${onePlusTwo}`}</div>;
};
