import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Layout } from '@/shared/components/Layout';
import { AllContentsPage } from '@/pages/feed/AllContents.tsx';
import { ContentWithCommentPage } from '@/pages/feed/ContentsWithComment.tsx';
import { PhotobookListPage } from '@/pages/albumlist/PhotobookListPage';
import { PhotobookPage } from '@/pages/albums/PhotobookPage';

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route
                    path="/"
                    element={
                        <Layout>
                            <AllContentsPage />
                        </Layout>
                    }
                />
                <Route
                    path="/comment"
                    element={
                        <Layout>
                            <ContentWithCommentPage />
                        </Layout>
                    }
                />
                <Route
                    path="/albums"
                    element={
                        <Layout>
                            <PhotobookListPage />
                        </Layout>
                    }
                />
                <Route path="/photobook" element={<PhotobookPage />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
