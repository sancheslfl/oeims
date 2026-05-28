import {createBrowserRouter, RouterProvider} from 'react-router-dom'
import {Dashboard} from "./pages/Dashboard.tsx";
import {AuthProvider, AuthRequire} from "./AuthContext.tsx";
import {Login} from "./pages/Login.tsx";



const app = createBrowserRouter([
    {
        path: "/",
        element: <Login/>
    },
    {
        path: "/dashboard",
        element: (
            <AuthRequire>
                <Dashboard />
            </AuthRequire>
            ),
    },
    /*{
        path: "*",
        element: <NotFound/>
    }*/
])

export function Router() {
    return <RouterProvider router={app}/>
}

export function App() {
    return (
        <AuthProvider>
            <Router />
        </AuthProvider>
    );
}