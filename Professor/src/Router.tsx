import {createBrowserRouter, RouterProvider} from 'react-router-dom'
import {Login} from "./Login.tsx";



const router = createBrowserRouter([
    {
        path: "/",
        element: <Login/>
    },
    /*{
        path: "*",
        element: <NotFound/>
    }*/
])

export function Router() {
    return <RouterProvider router={router}/>
}

export default Router
