<?php

require_once __DIR__ . '/ServiceFinder.php';

//$mode=urldecode($_REQUEST['mode']);

$production=TRUE;

try {
    if ($production==TRUE) {
        $serverFinder = new ServiceFinder();
        $threecategories = $serverFinder->findServices();
    } else {
        $threecategories=array();

        $threecategories['hosts'] = "My Hosts";
        $threecategories['services'] = "My Services";
        $threecategories['details'] = "My Details";
    }

} catch (Exception $e) {
    $threecategories['error'] = 'Caught exception: '.  $e->getMessage();
}

$json = json_encode($threecategories);

echo $json;

?>